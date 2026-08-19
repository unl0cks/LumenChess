# P5 Settings approved reference

The approved Iteration 3 Settings reference is stored as one deterministic lossless transport split across these active Base64 chunks:

- `.settings-iter3-approved-lossless.part00.b64` through `part03.b64`
- `.settings-iter3-approved-lossless.part04.00.b64` through `part04.05.b64`
- `.settings-iter3-approved-lossless.part05.00.b64` through `part05.05.b64`
- `.settings-iter3-approved-lossless.part06.b64`
- `.settings-iter3-approved-lossless.part07.b64`

`.github/workflows/p5-reference.yml` concatenates that exact list, validates the 85,352-character Base64 payload and transport SHA-256 `9fcfa2a94fbcf635c7d5b0dec3c1ae3af955a87377272350f18367c2d2842fca`, decodes the image, requires `390 × 844`, and verifies decoded RGB SHA-256 `791a5d6cac599e83a3083d369b42e2c39b09099cc59033255d6360b0ad906452` before generating the native comparison.

For visual QA, the decoded RGB digest is authoritative. Losslessly equivalent PNG/container bytes do not need the same file hash when their decoded pixels are identical. If the decoded pixels differ, the verification must fail.
