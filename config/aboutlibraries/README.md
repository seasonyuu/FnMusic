# Offline open-source notices

AboutLibraries 15.2.0 generates `R.raw.aboutlibraries` separately for each Android
build variant. The application uses its core parser and FnMusic's Compose UI;
no remote license request is made by the installed application.

`libraries/` and `licenses/` are maintained source inputs, not generated reports.
They supplement dependencies discovered from Gradle with source submodules,
ported algorithms and native dependencies that Maven metadata cannot describe.
The original license and notice texts are copied verbatim from:

- `third_party/accompanist-lyrics-{core,ui}/LICENSE`
- `third_party/licenses/liquid-glass-widgets.txt`
- `third_party/airplay2-sender/{LICENSE,NOTICE,licenses/THIRD-PARTY-NOTICES.txt}`
- `third_party/airplay2-sender/third_party/ed25519/LICENSE.txt`

Each license must explicitly set `hash` to the ID referenced by its library.
Without it the exporter hashes the content, leaving the library reference unresolved.

Update the associated version/revision, author and copied texts whenever these
pinned sources change. Mbed TLS is pinned by the sender's CMake configuration.
The sender's notices distinguish ported pyatv logic from pair_ap specification
references; neither upstream runtime is bundled. Preserve that distinction.

To inspect generated data, run `./gradlew :app:exportLibraryDefinitionsDebug
:app:exportLibraryDefinitionsRelease` (on one line). Automatic APK builds also
generate their own resources under `app/build/generated/aboutLibraries/`.
Do not commit generated output. Run `OpenSourcePackagingTest` to verify the
packaged catalogue includes direct, transitive and manual entries with local
license bodies, while excluding test dependencies.

The BSD-3-Clause override supplies Protocol Buffers' actual copyright notice
(from its upstream v3.25.5 LICENSE) for AndroidX's repackaged protobuf library,
instead of the SPDX template's placeholder fields. Revisit this override if a
second unrelated BSD dependency is added; give each project its own license ID.
