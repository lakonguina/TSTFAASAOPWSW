package com.tstfaasaopwsw

import com.tstfaasaopwsw.PendingQueue.Companion.WR
import com.tstfaasaopwsw.Store.Companion.K_WEB

/**
 * User-added web rules: extra patterns (usually a path, e.g.
 * `https://x.com/settings/content_you_see`) the user blocks on top of the built-in
 * whole-site and feed rules. They are theirs to add, edit and delete; the built-in rules
 * are not. Each pattern also carries its own block state ([Store.blockedWeb]) and schedule
 * (keyed `web:<pattern>`), exactly like the whole-site rule.
 */
private const val K_USER_RULES = "userRules"

fun Store.userRules(): Set<String> = sp.getStringSet(K_USER_RULES, emptySet())!!.toSet()

/** Adds a rule and blocks it right away (tightening = immediate). */
fun Store.addUserRule(pattern: String) {
    sp.edit().putStringSet(K_USER_RULES, userRules() + pattern).apply()
    blockWeb(pattern)
}

/**
 * Deletes a rule honouring the reflection delay: immediate when the delay is 0, otherwise
 * deferred like any other loosening (the block removal is queued, and when it matures the
 * rule object itself is purged — see [Store.applyDue]). Until then the rule stays blocked.
 */
fun Store.deleteUserRule(pattern: String) {
    if (delayMinutes <= 0L) removeUserRule(pattern) else unblockWeb(pattern)
}

/** Immediate full removal: drops the rule and clears its block and schedule. */
fun Store.removeUserRule(pattern: String) {
    putSets(K_WEB to blockedWeb - pattern)
    pending.cancel(WR, pattern)
    purgeUserRuleObject(pattern)
}

/** Drops the rule's bookkeeping (set membership + schedule) without touching [blockedWeb]. */
fun Store.purgeUserRuleObject(pattern: String) {
    sp.edit().putStringSet(K_USER_RULES, userRules() - pattern).apply()
    setWindow(ScheduleKeys.web(pattern), null)
    setQuota(ScheduleKeys.web(pattern), null)
}

/** Edits a rule's pattern: removes the old one entirely and adds the new, blocked. */
fun Store.renameUserRule(old: String, new: String) {
    if (new == old) return
    removeUserRule(old)
    addUserRule(new)
}
