package com.bigpapituna.sharpears.ui.family

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.family.Profile

/**
 * The profile playing now, provided at the root by MainActivity (from [Profiles.currentFlow], so a rename or a switch
 * to grown-up mode reaches every screen). Previews and tests without a provider get the owner.
 */
val LocalProfile = compositionLocalOf { Profile.owner() }

/** Everyone on the phone (the store), for the picker, the wall, the Family settings and duets. Provided by MainActivity. */
val LocalProfiles = staticCompositionLocalOf<Profiles?> { null }

// Small helpers for the other teams (see ui/family/INTEGRATION.md). Read them inside composables; they follow switches.

/** The profile playing now. */
@Composable @ReadOnlyComposable
fun profile(): Profile = LocalProfile.current

/** True when a kid is playing: the kid skin (bites, stars, no numbers, the goodbye) is on. */
@Composable @ReadOnlyComposable
fun isKid(): Boolean = LocalProfile.current.isKid

/** True when the player reads: letters on the colour notes. Always true for grown-ups; pre-readers get animals. */
@Composable @ReadOnlyComposable
fun reader(): Boolean = LocalProfile.current.let { !it.isKid || it.reader }

/** The hint escalation steps in seconds: glow, the hand points, the app plays it (kids 4/8/12, grown-ups 6/12/18). */
@Composable @ReadOnlyComposable
fun hintSeconds(): List<Int> = LocalProfile.current.hintSeconds

/** Boomwhacker colours on the notes and keys: on for kids, a choice for grown-ups. */
@Composable @ReadOnlyComposable
fun colourNotes(): Boolean = LocalProfile.current.colourNotes
