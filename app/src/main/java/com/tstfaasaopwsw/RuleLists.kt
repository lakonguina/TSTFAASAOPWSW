package com.tstfaasaopwsw

/**
 * Lists ("playlists") that organise rules, Spotify-style. A rule is a facet already known to
 * the enforcement brain — a blocked app (`app:pkg`), a blocked site/path (`web:pattern`) or a
 * hidden feed (`elem:id`). Every rule belongs to exactly one list; anything not explicitly
 * assigned sits in the default list, so lists are a pure view + assignment layer over the
 * existing block state ([Store.blocked]/[blockedWeb]/…), never a second source of truth.
 */
data class RuleList(val id: String, val name: String, val isDefault: Boolean, val sourceUrl: String? = null) {
    /** An imported blocklist (domains fetched from [sourceUrl]), not a hand-built list. */
    val imported: Boolean get() = sourceUrl != null
}

const val DEFAULT_LIST_ID = "default"
private const val K_LISTS = "ruleLists"
private const val K_MEMBER = "ruleMembers"
private const val LSEP = "\u001F"

fun Store.ruleLists(): List<RuleList> {
    val raw = sp.getStringSet(K_LISTS, null)
        ?: return listOf(RuleList(DEFAULT_LIST_ID, "My rules", true)).also { saveLists(it) }
    val lists = raw.mapNotNull { row ->
        val p = row.split(LSEP)
        if (p.size >= 3) RuleList(p[0], p[1], p[2] == "1", p.getOrNull(3)?.ifEmpty { null }) else null
    }
    val withDefault = if (lists.any { it.isDefault }) lists
    else lists + RuleList(DEFAULT_LIST_ID, "My rules", true)
    return withDefault.sortedWith(compareByDescending<RuleList> { it.isDefault }.thenBy { it.name.lowercase() })
}

private fun Store.saveLists(lists: List<RuleList>) =
    sp.edit().putStringSet(K_LISTS, lists.map {
        "${it.id}$LSEP${it.name}$LSEP${if (it.isDefault) "1" else "0"}$LSEP${it.sourceUrl ?: ""}"
    }.toSet()).apply()

fun Store.createList(name: String, sourceUrl: String? = null): String {
    val id = "l${System.currentTimeMillis()}"
    saveLists(ruleLists() + RuleList(id, name.ifBlank { "New list" }, false, sourceUrl))
    return id
}

fun Store.renameList(id: String, name: String) =
    saveLists(ruleLists().map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it })

/** Deletes a list (not the default); its rules fall back to the default list. */
fun Store.deleteList(id: String) {
    if (id == DEFAULT_LIST_ID) return
    val m = memberMap().filterValues { it != id }
    saveMembers(m)
    clearImportDomains(id) // no-op for a manual list
    saveLists(ruleLists().filterNot { it.id == id })
}

// --- deferred removal of an imported blocklist (loosening -> through the reflection delay) ---

/**
 * Removing an imported blocklist unblocks thousands of domains at once, so it is a loosening
 * and must defer like any other (unblock, lower delay). With a delay set, the list stays
 * active and a pending [PendingQueue.LR] matures the actual removal; with no delay it removes
 * now. The domains keep enforcing until maturation (they are never cleared early).
 */
fun Store.removeImportedList(id: String) {
    if (id == DEFAULT_LIST_ID) return
    if (delayMinutes <= 0L) finalizeImportedListRemoval(id)
    else if (pending.at(PendingQueue.LR, id) == null) pending.add(PendingQueue.LR, id, effective())
}

/** Effective instant a pending imported-list removal matures, or null. */
fun Store.pendingImportRemovalAt(id: String): Long? = pending.at(PendingQueue.LR, id)

/** The actual removal (called now, or by [Store.mature] when a deferred removal comes due). */
internal fun Store.finalizeImportedListRemoval(id: String) = deleteList(id)

private fun Store.memberMap(): Map<String, String> =
    sp.getStringSet(K_MEMBER, emptySet())!!.mapNotNull {
        val p = it.split(LSEP); if (p.size == 2) p[0] to p[1] else null
    }.toMap()

private fun Store.saveMembers(m: Map<String, String>) =
    sp.edit().putStringSet(K_MEMBER, m.map { "${it.key}$LSEP${it.value}" }.toSet()).apply()

/** The list a facet belongs to; the default list when it was never assigned elsewhere. */
fun Store.listIdOf(facet: String): String = memberMap()[facet] ?: DEFAULT_LIST_ID

fun Store.assignToList(facet: String, listId: String) {
    val m = memberMap().toMutableMap()
    if (listId == DEFAULT_LIST_ID) m.remove(facet) else m[facet] = listId
    saveMembers(m)
}

/** Drops a facet's list membership (used when its rule is deleted). */
fun Store.unassign(facet: String) {
    val m = memberMap().toMutableMap()
    if (m.remove(facet) != null) saveMembers(m)
}

/** Every facet that currently has a rule of any kind (block, feed hide, window or quota). */
fun Store.activeFacets(): Set<String> {
    val f = mutableSetOf<String>()
    blocked.forEach { f += ScheduleKeys.app(it) }
    blockedWeb.forEach { f += ScheduleKeys.web(it) }
    enabledElements.forEach { f += ScheduleKeys.elem(it) }
    f += windows().keys
    f += quotas().keys
    return f
}

/** The rules filed under [listId], newest-looking order left to the caller. */
fun Store.membersOf(listId: String): List<String> =
    activeFacets().filter { listIdOf(it) == listId }.sorted()

// --- list-level schedule (window + shared quota), keyed "list:<id>" ---

/** The schedule key for a list's own window/quota. */
fun listKey(listId: String) = "list:$listId"

/**
 * Seconds spent today across all of a list's members (its shared budget). Web/feed time is
 * the browser's own per-facet counter; app time is the system-measured foreground seconds
 * that [PolicyManager] folds into the same counters each reconcile (keyed `app:<pkg>`), so
 * the sum is uniform and app-inclusive without needing a [android.content.Context] here.
 */
fun Store.listUsedSeconds(listId: String): Int =
    membersOf(listId).filter { it.startsWith("web:") || it.startsWith("elem:") || it.startsWith("app:") }
        .sumOf { usedSecondsToday(it) }

/** Seconds left on a list's shared quota today (floored at 0), or null if the list has none. */
fun Store.listQuotaRemainingSeconds(listId: String): Int? {
    val q = quotaFor(listKey(listId)) ?: return null
    return (q * 60 - listUsedSeconds(listId)).coerceAtLeast(0)
}

private fun Store.listQuotaSpent(listId: String): Boolean {
    val q = quotaFor(listKey(listId)) ?: return false
    return listUsedSeconds(listId) >= q * 60
}

/** A facet's list closes it right now via the list window (any member) or its shared quota. */
fun Store.listBlocksFacet(facet: String, nowMin: Int, quotaCounts: Boolean): Boolean {
    val id = listIdOf(facet)
    val windowBlocks = windowFor(listKey(id))?.allows(nowMin) == false
    return windowBlocks || (quotaCounts && listQuotaSpent(id))
}

/** App packages that are members of a list carrying a shared quota (for usage metering). */
fun Store.quotaListAppPackages(): Set<String> =
    activeFacets().filter { it.startsWith("app:") && quotaFor(listKey(listIdOf(it))) != null }
        .map { it.removePrefix("app:") }.toSet()
