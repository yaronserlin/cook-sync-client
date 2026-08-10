# Code Quality Gatekeeper Review - Implementation Plan

This plan addresses violations of the project's code quality rules across the `cook-sync-client` Android repository.

## User Review Required

- **InputValidator Hardcoded Strings**: I am moving hardcoded error strings in `InputValidator` to `strings.xml`. This requires adding several new string resources.
- **BaseRepository DRY Refactoring**: I am adding helper methods to `BaseRepository` to reduce boilerplate in repository implementations.

## Proposed Changes

### [Common Infrastructure]

Reducing boilerplate and centralizing constants.

#### [BaseRepository.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/data/repository/BaseRepository.java)
- Add `executeAsync` and `fetchAsync` helper methods to reduce boilerplate in repository implementations.
- Add `protected static final long UNDO_WINDOW_MS = 3200;` and use it to replace hardcoded values in ViewModels.

#### [RetrofitClient.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/data/remote/RetrofitClient.java)
- Reuse a single `Gson` instance instead of creating one in every `buildRetrofit` call.

#### [InputValidator.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/util/InputValidator.java)
- Replace hardcoded error strings with calls to `CookSyncApplication.getAppContext().getString(R.string.*)`.

#### [strings.xml](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/res/values/strings.xml)
- Add missing error strings for input validation (e.g., `error_validation_email_blank`, `error_validation_password_policy`, etc.).

---

### [UI & ViewModels]

Fixing hygiene, error handling, and separation of concerns.

#### [AdminViewModel.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/ui/admin/AdminViewModel.java)
- Remove unused `isUsersLastPage()` method.
- Replace hardcoded `UNDO_WINDOW_MS` with `BaseRepository.UNDO_WINDOW_MS`.
- Use `Objects.equals()` for string comparisons.
- Replace simple `if` with `switch` or `Objects.equals` where suggested by analysis.

#### [HomeViewModel.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/ui/home/HomeViewModel.java)
- Replace hardcoded `UNDO_WINDOW_MS` with `BaseRepository.UNDO_WINDOW_MS`.

#### [CookingModeActivity.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/ui/recipe/cooking/CookingModeActivity.java)
- Fix empty `catch (Exception ignored)` by adding a log statement.

#### [AuthRepositoryImpl.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/data/repository/AuthRepositoryImpl.java)
- Fix empty `catch (IOException ignored)` by adding a log statement.
- Refactor methods to use the new `executeAsync` helper.

#### [RecipeRepositoryImpl.java](file:///Users/user/JavaAdvence/Final project/cookSync/cook-sync-client/app/src/main/java/com/cooksync/app/data/repository/RecipeRepositoryImpl.java)
- Refactor all methods to use the new `executeAsync` and `fetchAsync` helpers from `BaseRepository`.

## Verification Plan

### Automated Tests
- Since I cannot run tests, I will use `analyze_file` on all modified files to ensure no syntax errors or new warnings were introduced.

### Manual Verification
- Review diffs to ensure business logic is preserved.
- Verify that all new string resources are correctly referenced in `InputValidator`.
