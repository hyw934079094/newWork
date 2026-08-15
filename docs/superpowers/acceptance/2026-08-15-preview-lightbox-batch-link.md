# Acceptance: preview lightbox + batch link

Date: 2026-08-15

## Automated

- [x] `AssetLinkServiceTest` — `batchLinkOverwritesAndClears`, `batchLinkRejectsEmptyAndDeleted` PASS

## Manual

- [ ] Arc full preview: click cover → lightbox; click blank → close
- [ ] Arc full preview: click beat illustration → lightbox; click blank → close
- [ ] Workbench: check multiple thumbs → 批量关联 → pick character/series/arc → overwrite OK
- [ ] Workbench: batch linkType=无 → associations cleared
- [ ] Workbench: empty selection disables 批量关联
