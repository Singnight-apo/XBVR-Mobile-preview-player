# Management workflow optimization

User-authorized follow-up on `feat/xbvr-management`; keep main, version and published release unchanged.

## Interaction

- Open an unmatched file directly into candidate search. Derive an editable query from its basename: remove known video extensions, normalize Unicode width, and replace punctuation/symbols with spaces while retaining letters, numbers and combining marks. Blank derived queries never search the entire scene library.
- Keep covers, title, site and duration visible. Candidate and comparison pages offer a next-file action using the original filtered file order, across UI pages. Skipping does not write to the server and never wraps at the end. Matching remains explicitly confirmed and read back.
- Use specific toolbar titles for maintenance, scraping, matching, candidate search, comparison, file details and tasks. Keep the server identity below the toolbar and remove the duplicate content-area back button.

## Performance

- Update task labels and the transient status area without rebuilding the page for each event/poll/request start. Avoid repeated profile decryption in image/task callbacks by comparing the loaded immutable profile and generation.
- Bound mounted rows to 20 files/sites or 12 candidates per page; preserve filters and page selection on rotation. Load cover images when visible and cancel old image requests on navigation.
- Separate read-only requests, which may reuse connections, from mutation requests, which retain fresh sockets/no retries. Cancel abandoned reads on navigation without cancelling a mutation or its verification reads. Cache supported-route lookup and avoid duplicate capability discovery.

## Verification

- JVM tests for filename cleanup, empty filenames, queue boundaries and read-cancellation isolation; existing API/auth/mutation/readback tests remain mandatory.
- Android build/lint, then device tests against the loopback synthetic fixture with 202 sites, 204 files and 29 scenes. Check automatic query, empty results/skip, page boundaries, toolbar/back behavior, orientation and visible cover requests.
- Collect a bounded `gfxinfo framestats` comparison and report its limits. Real server operations remain read-only during validation.
