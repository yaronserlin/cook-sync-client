# CookSync Client — mandatory skill checks

Any agent working in this directory (`cook-sync-client`, the Android Java/XML
client) MUST invoke both of the following skills before making changes, and
follow their instructions for the duration of the task:

1. `/mobile-feature-integrator` — engineering workflow: codebase review
   first, plan-then-stop-and-ask before implementing, MVVM, pagination, JWT
   interceptors, Cloudinary DTO, Javadoc, DRY XML.
2. `/design-review` — validates the screen/layout being touched against the
   design deck and the "Organic" design system before or after the change.

This applies to every task on the client side — new features, bug fixes,
refactors, or design integration — not only large feature work.
