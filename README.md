# Lardr

An Android grocery shopping app built around the reality of how households actually shop: recurring items, shared lists, and recipes that map directly to a weekly cart.

---

## Problem

Generic shopping list apps treat every item as a one-off note. They ignore recurrence ("we buy milk every week"), shared ownership across family members or flatmates, and the gap between a recipe and an actual cart. The result is lists rebuilt from scratch every week, duplicated effort across devices, and no connection between what you cook and what you buy.

## Solution

Lardr models a household's shopping workflow end-to-end:

- **Stores** are shared spaces — multiple users can collaborate on the same list in real time.
- **Starred ingredients** have a configurable periodicity and auto-populate the list on the correct week.
- **Recipes** map directly to shopping list entries, with duplicate detection and per-ingredient conflict resolution (ignore / increase quantity / replace).
- **Swipe gestures** mark items as bought or delete them inline, keeping the interaction fast and one-handed.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM + Unidirectional Data Flow |
| DI | Hilt |
| Navigation | Navigation Compose (single-activity) |
| Async | Kotlin Coroutines + StateFlow |
| Backend | Firebase Auth + Firestore |
| Local persistence | DataStore (preferences), SharedPreferences (WAL) |
| Build | Gradle Version Catalogs (TOML) |

---

## Architecture & Engineering Decisions

**MVVM with strict separation of concerns** — Models are pure Kotlin `data class`es. ViewModels own all business logic and expose immutable `UiState` via `StateFlow`. Composables are stateless and driven entirely by state, with no logic of their own.

**Repository pattern** — A `FirebaseDataSource` abstraction isolates all Firestore and Auth SDK calls. Repositories expose `Flow`-based streams and suspend functions, keeping ViewModels unaware of the underlying SDK.

**Optimistic UI** — Every mutation updates local state and a reactive `StoreCache` (backed by `StateFlow`) before the network call. The Home screen observes the cache directly so counts and names stay consistent across screens without a Firestore round-trip.

**Write-Ahead Log for offline resilience** — Critical mutations (rename, mark bought, delete) are serialised to a `SharedPreferences`-backed WAL using synchronous `commit()` before the Firestore call is made. On app restart, `LardrApplication.onCreate` replays any unconfirmed writes. Snapshot listeners overlay pending WAL state on every Firestore emission, preventing stale server responses from reverting unconfirmed changes. This guarantees correctness even if the process is killed immediately after user interaction.

**Application-scoped coroutines for writes** — A `SupervisorJob`-backed `CoroutineScope` injected via Hilt outlives ViewModel lifecycle, ensuring fire-and-forget Firestore writes are not cancelled when the user navigates away.

**Firestore offline persistence + WAL** — Firestore's built-in offline cache handles network unavailability; the WAL layer handles process death before the SDK can flush its own pending writes to disk.
