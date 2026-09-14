# network

Owner: **Jianing Xia** (Weather, Maps and Public Transport APIs module).

Retrofit/OkHttp clients for weather, route/maps, public transport and AI-provider APIs live
here. Implement [`ExternalDataRepository`](../data/repository/ExternalDataRepository.kt) against
this package's clients, then wire it into `externalDataRepository` in
[`core/di/AppContainer.kt`](../core/di/AppContainer.kt) (search for `TODO(Jianing)`) — reuse the
shared `okHttpClient` already provided there.

Due dates and evidence: see `docs/WORK_PLAN.md` at the repo root.

Delete this file once real source files exist in this package.
