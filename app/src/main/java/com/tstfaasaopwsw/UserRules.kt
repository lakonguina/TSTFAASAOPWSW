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

/** Deletes a user rule outright: drops it and clears its block and schedule. */
fun Store.removeUserRule(pattern: String) {
    sp.edit().putStringSet(K_USER_RULES, userRules() - pattern).apply()
    putSets(K_WEB to blockedWeb - pattern)
    pending.cancel(WR, pattern)
    setWindow(ScheduleKeys.web(pattern), null)
    setQuota(ScheduleKeys.web(pattern), null)
}

/** Edits a rule's pattern: removes the old one entirely and adds the new, blocked. */
fun Store.renameUserRule(old: String, new: String) {
    if (new == old) return
    removeUserRule(old)
    addUserRule(new)
}
