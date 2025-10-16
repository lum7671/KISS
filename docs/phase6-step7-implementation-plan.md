# Phase 6 Step 7: SettingsActivity Fragment Conversion - Implementation Plan

## Executive Summary

**Task**: Convert SettingsActivity from PreferenceActivity to PreferenceFragmentCompat architecture  
**Estimated Time**: 6-8 hours  
**Complexity**: Very High (largest migration task in Phase 6)  
**Branch**: `feature/phase6-step7-fragment-conversion`  
**Status**: 🔄 In Progress - Analysis Complete

---

## Current Architecture Analysis

### SettingsActivity.java (859 lines)

**Current State**:

```java
public class SettingsActivity extends PreferenceActivity 
    implements SharedPreferences.OnSharedPreferenceChangeListener {
```

**Key Features**:

1. **Preference Loading**: `addPreferencesFromResource(R.xml.preferences)` (line 116)
2. **SharedPreferences Listener**: Registers in `onResume()`, unregisters in `onPause()`
3. **Dynamic Preferences**: Creates preferences programmatically
   - ExcludePreferenceScreen (3 instances)
   - Custom search providers (MultiSelectListPreference)
   - Default search provider (ListPreference)
   - Tags selection (MultiSelectListPreference × 2)
   - Item-to-run list (ListPreference with dependency)
4. **Lifecycle Management**: onCreate, onResume, onPause
5. **Menu Handling**: onCreateOptionsMenu, onMenuItemSelected
6. **Permission Handling**: onRequestPermissionsResult
7. **System UI**: SystemUiVisibilityHelper, InterfaceTweaks
8. **Restart Logic**: requireFullRestart flag for settings requiring reload

---

## Target Architecture

### New Structure

```
SettingsActivity.java (100-150 lines)
├── extends AppCompatActivity
├── Toolbar setup
├── Fragment transaction
├── Intent handling
├── Permission forwarding
└── System UI management

SettingsFragment.java (700-800 lines)
├── extends PreferenceFragmentCompat
├── onCreatePreferences() - Load R.xml.preferences
├── SharedPreferences listener
├── Dynamic preference creation
├── Business logic
└── Data handler interaction
```

---

## Migration Strategy

### Phase A: Create SettingsFragment (3-4 hours)

**Step A1: Base Fragment Setup** (30min)

- Create `SettingsFragment.java`
- Extend `PreferenceFragmentCompat`
- Implement `onCreatePreferences(Bundle, String)`
- Move `addPreferencesFromResource(R.xml.preferences)`

**Step A2: Move Initialization Logic** (1h)

- Move `onCreate()` business logic to `onCreatePreferences()`
- Move version conditional logic (API level checks)
- Move `removePreference()` calls
- Move `fixSummaries()` call
- Move async initialization (`CoroutineUtils.execute(runnable)`)

**Step A3: Move Dynamic Preference Creation** (1h)

- Move `addExcludedAppSettings()` → Update to use `ExcludePreferenceScreenCompat`
- Move `addExcludedFromHistoryAppSettings()` → Update to use `ExcludePreferenceScreenCompat`
- Move `addExcludedShortcutAppSettings()` → Update to use `ExcludePreferenceScreenCompat`
- Move `addCustomSearchProvidersPreferences()`
- Move `addHiddenTagsTogglesInformation()`
- Move `addTagsFavInformation()`
- Move `setAdditionalContactsData()`
- Move `setListPreferenceIconsPacksData()`

**Step A4: Move Helper Methods** (30min)

- Move all private helper methods
- Move `getParent()` methods
- Move `removePreference()`, `removeSearchProviderSelect()`, etc.
- Move `createCustomSearchProvidersPreference()`
- Move `addDefaultSearchProvider()`
- Move `generateItemToRunListContent()`, `asyncInitItemToRunList()`, `updateItemToRunList()`
- Move `reorderPreferencesWithDisplayDependency()`

**Step A5: Lifecycle & Listener** (1h)

- Implement lifecycle methods:
  - `onResume()` → Register SharedPreferences listener
  - `onPause()` → Unregister listener
- Move `onSharedPreferenceChanged()` implementation
- Handle `requireFullRestart` flag
- Implement `setPhoneHistoryEnabled()`

### Phase B: Update SettingsActivity (1-2 hours)

**Step B1: Change Base Class** (15min)

- Change `extends PreferenceActivity` → `extends AppCompatActivity`
- Remove PreferenceActivity-specific imports
- Add FragmentActivity imports

**Step B2: Implement Activity Layout** (30min)

- Create simple layout with Toolbar + FrameLayout container
- Or use AppCompatActivity's default content view with Fragment transaction

**Step B3: Fragment Transaction** (30min)

- In `onCreate()`:
  - Setup theme (keep existing logic)
  - Setup SystemUiVisibilityHelper
  - Setup orientation lock
  - Load SettingsFragment via FragmentManager

**Step B4: Menu & Permission Forwarding** (30min)

- Keep `onCreateOptionsMenu()`
- Keep `onMenuItemSelected()`
- Keep `onRequestPermissionsResult()` → Forward to PermissionManager
- Keep `onWindowFocusChanged()` → SystemUiVisibilityHelper

### Phase C: Update ExcludePreferenceScreen Usage (30min)

**Original Usage (3 places)**:

```java
PreferenceScreen excludedAppsScreen = ExcludePreferenceScreen.getInstance(
    this,  // PreferenceActivity
    R.string.ui_excluded_apps,
    R.string.ui_excluded_apps_dialog_title,
    new ExcludePreferenceScreen.OnExcludedListener() { ... },
    AppPojo::isExcluded
);
```

**New Usage**:

```java
PreferenceScreen excludedAppsScreen = ExcludePreferenceScreenCompat.getInstance(
    requireContext(),  // Context
    getPreferenceManager(),  // PreferenceManager
    R.string.ui_excluded_apps,
    R.string.ui_excluded_apps_dialog_title,
    new ExcludePreferenceScreenCompat.OnExcludedListener() { ... },
    AppPojo::isExcluded
);
```

**Changes**:

- Add `getPreferenceManager()` parameter (androidx requires explicit PreferenceManager)
- `this` → `requireContext()`
- `ExcludePreferenceScreen` → `ExcludePreferenceScreenCompat`
- Update interface names

### Phase D: Testing & Validation (1 hour)

**Build Validation**:

- `./gradlew assembleDebug --quiet` → 0 warnings expected
- Verify all imports resolved
- Verify no deprecated API usage

**Functional Testing**:

1. Launch Settings activity
2. Navigate through all preference screens
3. Test ExcludePreferenceScreen (3 types)
4. Change preferences and verify listener
5. Test menu items (Help link)
6. Test restart-requiring settings
7. Test permission requests (phone history)
8. Verify theme application
9. Test back navigation

**Edge Cases**:

- Rotate device (savedInstanceState handling)
- Background -> Foreground (lifecycle)
- Permission denial flow
- Empty app list scenarios

---

## Technical Challenges

### Challenge 1: PreferenceActivity-specific APIs

**Problem**: Many methods are PreferenceActivity-specific

- `getPreferenceScreen()`
- `findPreference()`
- `addPreferencesFromResource()`

**Solution**: PreferenceFragmentCompat equivalents exist

- `getPreferenceScreen()` - Available in Fragment
- `findPreference()` - Available in Fragment (with generic type parameter)
- `addPreferencesFromResource()` → `setPreferencesFromResource()` in `onCreatePreferences()`

### Challenge 2: Context References

**Problem**: `this` refers to PreferenceActivity

- `new ListPreference(this)`
- `new MultiSelectListPreference(this)`

**Solution**: Use Fragment context

- `new ListPreference(requireContext())`
- Or use `getPreferenceManager().createPreferenceScreen(requireContext())`

### Challenge 3: Toolbar Handling

**Problem**: PreferenceActivity auto-manages toolbar for PreferenceScreen dialogs

- `onPreferenceTreeClick()` finds toolbar and sets navigation

**Solution**: androidx.preference.PreferenceScreen handles this automatically

- Remove `onPreferenceTreeClick()` logic (or simplify)
- Still need to apply theme colors and system bar insets

### Challenge 4: Async Initialization State

**Problem**: `savedInstanceState` handling for async operations

```java
if (savedInstanceState == null) {
    CoroutineUtils.execute(runnable);  // Async
    asyncInitItemToRunList();
} else {
    runnable.run();  // Sync to restore state
    // Sync init ItemToRunListContent
}
```

**Solution**: Keep same logic in Fragment

- Fragment has `savedInstanceState` in `onCreate()` and `onCreatePreferences()`
- Preserve sync/async branching

### Challenge 5: FindPreference Type Casting

**Problem**: PreferenceActivity `findPreference()` returns `Preference`

```java
ListPreference iconsPack = (ListPreference) findPreference("icons-pack");
```

**Solution**: PreferenceFragmentCompat has generic version

```java
ListPreference iconsPack = findPreference("icons-pack");
// No cast needed if using Kotlin, but in Java still need cast
```

**Actually in Java**: Still need cast, no change

```java
ListPreference iconsPack = (ListPreference) findPreference("icons-pack");
```

---

## Code Organization

### Files to Create

- `app/src/main/java/fr/neamar/kiss/SettingsFragment.java` (new, 700-800 lines)

### Files to Modify

- `app/src/main/java/fr/neamar/kiss/SettingsActivity.java` (859 → 100-150 lines)

### Files to Reference

- `app/src/main/java/fr/neamar/kiss/preference/ExcludePreferenceScreenCompat.java` (created in Step 6)
- All Step 1-5 created Compat preferences (will be used in SettingsFragment)

---

## Implementation Checklist

### Pre-Implementation

- [x] Read and analyze SettingsActivity.java (859 lines)
- [x] Identify all lifecycle methods
- [x] Identify all dynamic preference creation
- [x] Identify all helper methods
- [x] Understand ExcludePreferenceScreen usage
- [x] Understand savedInstanceState handling
- [x] Document implementation plan

### Phase A: SettingsFragment

- [ ] Create SettingsFragment.java base class
- [ ] Implement onCreatePreferences()
- [ ] Move addPreferencesFromResource()
- [ ] Move API level conditional logic
- [ ] Move fixSummaries() and async initialization
- [ ] Move all addExcluded*Settings() methods
- [ ] Update ExcludePreferenceScreen → ExcludePreferenceScreenCompat (3 calls)
- [ ] Move all custom search provider methods
- [ ] Move tags-related methods
- [ ] Move all helper methods (getParent, removePreference, etc.)
- [ ] Move ItemToRunList logic
- [ ] Implement onResume() with listener registration
- [ ] Implement onPause() with listener unregistration
- [ ] Move onSharedPreferenceChanged() implementation
- [ ] Move setPhoneHistoryEnabled()
- [ ] Add getDataHandler() helper

### Phase B: SettingsActivity

- [ ] Change extends PreferenceActivity → AppCompatActivity
- [ ] Update imports
- [ ] Keep theme and SystemUiVisibilityHelper setup
- [ ] Add Fragment transaction in onCreate()
- [ ] Keep onCreateOptionsMenu()
- [ ] Keep onMenuItemSelected()
- [ ] Keep onRequestPermissionsResult()
- [ ] Keep onWindowFocusChanged()
- [ ] Remove all business logic (moved to Fragment)

### Phase C: Build & Test

- [ ] ./gradlew assembleDebug --quiet → 0 warnings
- [ ] Test settings activity launch
- [ ] Test all preference screens
- [ ] Test ExcludePreferenceScreen (3 types)
- [ ] Test SharedPreferences changes
- [ ] Test menu items
- [ ] Test restart-requiring settings
- [ ] Test permission flow
- [ ] Test device rotation
- [ ] Test theme application

### Phase D: Documentation

- [ ] Create phase6-step7-completion-report.md
- [ ] Update phase6-progress-tracker.md
- [ ] Document any issues encountered
- [ ] Document testing results
- [ ] Git commit with detailed message

---

## Risk Assessment

### High Risk Areas

1. **savedInstanceState Handling** (Medium-High Risk)
   - Complex sync/async branching logic
   - Must preserve exact behavior for state restoration
   - Mitigation: Careful testing of rotation and background/foreground

2. **Dynamic Preference Creation** (Medium Risk)
   - 8+ methods creating preferences programmatically
   - ExcludePreferenceScreen called 3 times with different callbacks
   - Mitigation: Test each preference type individually

3. **SharedPreferences Listener** (Medium Risk)
   - Complex logic with 15+ key-specific handlers
   - Triggers DataHandler reloads, Activity recreate(), permission requests
   - Mitigation: Verify each handler still works in Fragment context

4. **Fragment Lifecycle** (Low-Medium Risk)
   - Different lifecycle than Activity
   - onResume/onPause timing may differ
   - Mitigation: Use Fragment lifecycle methods correctly

### Low Risk Areas

1. **Static Utility Methods**: Can be moved as-is
2. **Menu Handling**: Simple forwarding to Fragment
3. **Permission Handling**: PermissionManager abstraction unchanged
4. **Theme Application**: Already using static InterfaceTweaks

---

## Estimated Timeline

| Phase | Task | Time | Cumulative |
|-------|------|------|------------|
| A1 | Base Fragment Setup | 30min | 30min |
| A2 | Move Initialization Logic | 1h | 1.5h |
| A3 | Move Dynamic Preference Creation | 1h | 2.5h |
| A4 | Move Helper Methods | 30min | 3h |
| A5 | Lifecycle & Listener | 1h | 4h |
| B1-B2 | Activity Base Class & Layout | 45min | 4.75h |
| B3-B4 | Fragment Transaction & Forwarding | 1h | 5.75h |
| C | ExcludePreferenceScreen Updates | 30min | 6.25h |
| D | Testing & Validation | 1h | 7.25h |
| Doc | Documentation | 30min | 7.75h |

**Total Estimated Time**: 7.75 hours (within 6-8h range)

---

## Success Criteria

1. ✅ **Build Success**: `./gradlew assembleDebug --quiet` → 0 warnings
2. ✅ **No Deprecated APIs**: All android.preference.*replaced with androidx.preference.*
3. ✅ **Functional Equivalence**: All features work identically to before
4. ✅ **UI Consistency**: Visual appearance unchanged
5. ✅ **Lifecycle Correct**: No memory leaks, proper listener registration/unregistration
6. ✅ **Testing Complete**: All preference types tested, edge cases verified
7. ✅ **Documentation**: Completion report written, progress tracker updated

---

## Next Steps After This Document

1. **Start Implementation**: Create `SettingsFragment.java` base class
2. **Follow Phases A-D**: Work through checklist systematically
3. **Test Incrementally**: Build after each major change
4. **Document Progress**: Update this plan if strategy changes

---

## Notes

- This is the largest single migration in Phase 6 (859 lines → 2 files)
- ExcludePreferenceScreenCompat created in Step 6 makes this possible
- All DialogPreference Compat classes from Steps 1-5 will be used
- After this step, only cleanup (Step 8) remains
- This completes the architectural migration from PreferenceActivity → Fragment

**Current Status**: Ready to begin implementation (Phase A1)

---

*Document Created*: 2025-01-XX  
*Last Updated*: 2025-01-XX  
*Author*: GitHub Copilot  
*Phase 6 Progress*: Step 7/8 (87.5% complete after this step)
