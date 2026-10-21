package com.bigpapituna.sharpears.ui.brief

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bigpapituna.sharpears.engine.brief.ActionInput
import com.bigpapituna.sharpears.engine.brief.ActionRules
import com.bigpapituna.sharpears.engine.brief.AskHistory
import com.bigpapituna.sharpears.engine.brief.AskRules
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefActions
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Outcome
import com.bigpapituna.sharpears.engine.brief.Performed
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Where a brief is: reading the log, writing, shown, or nothing to say yet. */
enum class Phase { LOADING, READY, EMPTY }

/** What one action card is doing. */
sealed interface CardState {
    data object Idle : CardState
    /** The sheet is open: [step] 1 or 2 (a fresh start asks twice); Teacher mode's pick and note. */
    data class Confirm(val step: Int = 1, val song: String? = null, val note: String = "") : CardState
    data object Working : CardState
    data class Done(val performed: Performed, val line: String, val canUndo: Boolean = performed.reversible) : CardState
    data class Failed(val reason: String) : CardState
}

/**
 * One open brief: one person, one period. Findings first (from the event log), then the brief (from the writers); the
 * action cards' states and the question history live with it, so switching tab and back keeps them.
 */
@Stable
class BriefPageState(val profile: Profile, val period: Period) {
    var phase by mutableStateOf(Phase.LOADING)
    var findings by mutableStateOf<Findings?>(null)
    var brief by mutableStateOf<Brief?>(null)
    /** A Refresh is rewriting the brief; the old one stays on screen meanwhile. */
    var refreshing by mutableStateOf(false)
    /** The phone's brief is showing while the AI writes its own (the day and the week); it is swapped in when it comes. */
    var improving by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    var asks by mutableStateOf(AskHistory())
    var expanded by mutableStateOf(false)
    val cards = mutableStateMapOf<Int, CardState>()

    /** The recommendations a screen draws: the brief's, minus actions this kind of person never gets, at most three. */
    val recommendations: List<Brief.Recommendation>
        get() = brief?.recommendations.orEmpty().filter { ActionRules.allowed(profile.kind, it.candidate) }.take(3)
}

/**
 * Loads, writes, refreshes, answers and acts for the open briefs. One per screen; [scope] is the screen's. Work that reads
 * files or calls a writer runs on the IO dispatcher through [BriefEnv]; the page states are Compose state.
 */
@Stable
class BriefController(val env: BriefEnv, private val scope: CoroutineScope) {
    private val pages = HashMap<String, BriefPageState>()
    private val open = ArrayList<Performed>()
    private val services = HashMap<String, BriefService>()

    /** [profile]'s writers, made once per screen. */
    private fun service(profile: Profile): BriefService = services.getOrPut(profile.id) { env.serviceFor(profile) }

    /** The state for [profile] and [period], loading it the first time it is asked for. */
    fun page(profile: Profile, period: Period): BriefPageState = pages.getOrPut("${profile.id}/${period.name}") {
        BriefPageState(profile, period).also { load(it) }
    }

    private fun load(p: BriefPageState) {
        scope.launch {
            p.phase = Phase.LOADING
            val f = withContext(Dispatchers.IO) { runCatching { env.loadFindings(p.profile, p.period, env.now()) }.getOrNull() }
            p.findings = f
            if (f == null || f.isEmpty) { p.phase = Phase.EMPTY; return@launch }
            val service = service(p.profile)
            val name = p.profile.name
            if (p.period != Period.SESSION && service.aiMayWrite(f)) {
                // The day and the week (engine/brief/ai/INTEGRATION.md, "Waiting"): the AI may take seconds, so the phone's
                // brief shows at once and the AI's replaces it when it comes. A reply from the AI's cache comes at once:
                // waiting a moment for it first saves the page from changing under the reader.
                val ai = async { service.write(f, name) }
                val soon = withTimeoutOrNull(QUICK_MS) { ai.await() }
                if (soon != null) { show(p, soon); return@launch }
                service.phone(f, name)?.let { show(p, it) }
                p.improving = true
                val better = ai.await()
                p.improving = false
                // Swapped in only while nobody has started on the phone's version (an action card, a Refresh).
                if (better != null && (p.brief == null || (better.writtenBy == Brief.Writer.AI && p.cards.isEmpty() && !p.refreshing))) show(p, better)
                if (p.brief == null) { p.phase = Phase.EMPTY; p.notice = "I could not write this one just now. Try Refresh in a moment." }
                return@launch
            }
            val b = service.write(f, name)
            if (b == null) { p.phase = Phase.EMPTY; p.notice = "I could not write this one just now. Try Refresh in a moment."; return@launch }
            show(p, b)
        }
    }

    private fun show(p: BriefPageState, b: Brief) { p.brief = b; p.phase = Phase.READY }

    /** Rewrites the brief from fresh findings (the log may have grown since). The old brief stays until the new one is ready. */
    fun refresh(p: BriefPageState) {
        if (p.refreshing) return
        scope.launch {
            p.refreshing = true; p.notice = null
            val f = withContext(Dispatchers.IO) { runCatching { env.loadFindings(p.profile, p.period, env.now()) }.getOrNull() } ?: p.findings
            val b = f?.let { service(p.profile).refresh(it, p.profile.name) }
            if (b != null && f != null) {
                p.findings = f; p.brief = b; p.phase = if (f.isEmpty) Phase.EMPTY else Phase.READY
                p.cards.clear()
            } else p.notice = "I could not rewrite it just now. The one above is still good."
            p.refreshing = false
        }
    }

    /** Puts [text] to the writer as a question about this brief. The name is swapped out first (AskRules.forWriter). */
    fun ask(p: BriefPageState, text: String) {
        val f = p.findings ?: return
        if (p.asks.busy) return
        val (next, id) = p.asks.ask(text) ?: return
        p.asks = next
        val question = AskRules.forWriter(next.exchanges.first { it.id == id }.question, p.profile.name, p.profile.kind)
        scope.launch {
            val a = service(p.profile).answer(f, p.profile.name, question)
            p.asks = if (a == null) p.asks.failed(id) else p.asks.answered(id, a)
        }
    }

    // ---- the action cards ----

    private fun actions(p: BriefPageState) = BriefActions(env.effectsFor(p.profile), env.titles, p.profile.name,
        record = { id, params -> env.record(p.profile, id, params) }, clock = env.now)

    fun actionsFor(p: BriefPageState): BriefActions = actions(p)

    /** The card's button was pressed (the gate has been passed): open its sheet. */
    fun openSheet(p: BriefPageState, index: Int) { p.cards[index] = CardState.Confirm() }

    fun closeSheet(p: BriefPageState, index: Int) { p.cards[index] = CardState.Idle }

    fun update(p: BriefPageState, index: Int, state: CardState) { p.cards[index] = state }

    /** The last "yes" of the sheet: do it. A success keeps its Undo until [commitAll] or the window ends. */
    fun perform(p: BriefPageState, index: Int, c: Candidate, input: ActionInput) {
        p.cards[index] = CardState.Working
        scope.launch {
            val a = actions(p)
            val outcome = withContext(Dispatchers.Default) { a.perform(c, input) }
            when (outcome) {
                is Outcome.Failed -> p.cards[index] = CardState.Failed(outcome.reason)
                is Outcome.Done -> {
                    val perf = outcome.performed
                    synchronized(open) { open += perf }
                    p.cards[index] = CardState.Done(perf, a.view(c).doneLine)
                    // Recorded once the Undo window has passed, or when the screen closes ([commitAll]).
                    launch { delay(BriefActions.UNDO_WINDOW_MS); perf.commit(); if (perf.committed) refreshDone(p, index) }
                }
            }
        }
    }

    private fun refreshDone(p: BriefPageState, index: Int) {
        val s = p.cards[index]
        if (s is CardState.Done && s.performed.committed) p.cards[index] = s.copy(canUndo = false)
    }

    fun undo(p: BriefPageState, index: Int) {
        val s = p.cards[index] as? CardState.Done ?: return
        if (s.performed.undo()) p.cards[index] = CardState.Idle
    }

    /** The screen is closing: whatever is still undoable is recorded now. */
    fun commitAll() { synchronized(open) { open.forEach { it.commit() }; open.clear() } }

    companion object {
        /** How long a day or a week waits for the AI before the phone's brief shows (a cached reply is back well within it). */
        const val QUICK_MS = 400L
    }
}
