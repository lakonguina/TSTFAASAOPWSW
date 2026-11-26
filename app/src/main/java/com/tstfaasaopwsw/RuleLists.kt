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

/** Browser-measured seconds spent today across a list's web/feed members (its shared budget). */
fun Store.listWebUsedSeconds(listId: String): Int =
    membersOf(listId).filter { it.startsWith("web:") || it.startsWith("elem:") }.sumOf { usedSecondsToday(it) }

/** Seconds left on a list's shared quota today (floored at 0), or null if the list has none. */
fun Store.listQuotaRemainingSeconds(listId: String): Int? {
    val q = quotaFor(listKey(listId)) ?: return null
    return (q * 60 - listWebUsedSeconds(listId)).coerceAtLeast(0)
}

private fun Store.listQuotaSpent(listId: String): Boolean {
    val q = quotaFor(listKey(listId)) ?: return false
    return listWebUsedSeconds(listId) >= q * 60
}

/** A facet's list closes it right now via the list window (any member) or shared quota (web/feed). */
fun Store.listBlocksFacet(facet: String, nowMin: Int, quotaCounts: Boolean): Boolean {
    val id = listIdOf(facet)
    val windowBlocks = windowFor(listKey(id))?.allows(nowMin) == false
    return windowBlocks || (quotaCounts && listQuotaSpent(id))
}
