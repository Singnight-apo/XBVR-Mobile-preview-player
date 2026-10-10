# Matching file preview

Approved in chat: full-screen preview from candidate and comparison pages, retaining matching search, selected candidate and scroll position on return. Also expose preview in the matched-file details page.

Reuse PlayerActivity, projection controls and the current profile's authenticated media transport. Load `/deovr/file/{id}` directly, including unmatched files, instead of resolving a scene or choosing its first file. Validate the returned media belongs to the requested file and server; append `dnt=true` to media requests. No HereSphere fallback or server favorite writes in preview. Report unsupported/authentication/playback failures through the existing player error flow.

Keep management state owned by the underlying Activity, capture draft/scroll before launching, and pause subscriptions normally while the player owns the foreground. Recreate from the existing management saved state if Android reclaims the page. No database mutation, main merge, version bump or release.

Validation: mock-server tests for unmatched-file loading, credentials, wrong-file rejection and no fallback/writes; phone test with synthetic video, seeking, rotation and return to candidate/comparison context. Performance evidence remains separate from playback compatibility claims.
