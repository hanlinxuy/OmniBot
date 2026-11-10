# iOSWorld → Android 逐项映射

完整 prompt、逐条 rubric、固定源版本、历史结果及差异见同目录 `android-unified-scenarios.json`。

334 个来源变体，333 个保守归并场景；仅闹钟的一对共用模板，各自验收。没有把相似应用或局部动作当成重复完整任务。

状态来自迁移依赖判定及独立读回结果，不是 133 项真机通过。银行/支付排除整项；原 iOS 界面条款不会因 Android 文件/API 操作而算通过。

| 来源 ID | Android 能力对应 | 当前迁移状态 |
|---|---|---|
| caltrack-001 | caltrack → isolated nutrition store | android_file_adaptation_passed |
| cinephile-001 | cinephile → isolated movie catalog | android_file_adaptation_passed |
| cityride-001 | cityride → isolated ride service | android_file_adaptation_passed |
| clock-001 | clock → Android Clock Intent / scoped clock controls | native_adapter_candidate |
| clouddocs-004 | clouddocs → phone Pi document files | android_file_adaptation_passed |
| clouddrive-001 | clouddrive → scoped document metadata service | android_file_adaptation_passed |
| cloudsheets-003 | cloudsheets → phone Pi XLSX files | android_file_adaptation_passed |
| cloudslides-002 | cloudslides → phone Pi PPTX files | android_file_adaptation_passed |
| dinespot-001 | dinespot → isolated restaurant service | requires_isolated_source_fixtures |
| freshcart-001 | freshcart → isolated grocery catalog/cart | android_file_adaptation_passed |
| lockedin-003 | lockedin → isolated professional-network fixture | android_file_adaptation_passed |
| lockedin-004 | lockedin → isolated professional-network fixture | android_file_adaptation_passed |
| mail-002 | mail → Android mock Mail / scoped mail fixture | android_file_adaptation_passed |
| megamart-001 | megamart → isolated shopping catalog/cart | android_file_adaptation_passed |
| mybank-003 | mybank → EXCLUDED | excluded_user_scope |
| notes-001 | notes → vivo Notes controls or isolated note store | native_adapter_candidate |
| quickbite-001 | quickbite → isolated food-order service | requires_isolated_source_fixtures |
| quickchat-003 | quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| scorezone-003 | scorezone → isolated sports feed | requires_isolated_source_fixtures |
| skytrip-002 | skytrip → isolated flight search/history | requires_isolated_source_fixtures |
| splitpay-001 | splitpay → EXCLUDED | excluded_user_scope |
| stayfinder-001 | stayfinder → isolated lodging search/history | android_file_adaptation_passed |
| tasterank-001 | tasterank → isolated restaurant list | android_file_adaptation_passed |
| teamchat-003 | teamchat → isolated Mattermost fixture | android_file_adaptation_passed |
| ticketbox-004 | ticketbox → isolated event catalog/history | android_file_adaptation_passed |
| trailblaze-004 | trailblaze → isolated activity store | android_file_adaptation_passed |
| weather-004 | weather → fixed-date weather service or Android weather controls | android_file_adaptation_passed |
| lockedin-001 | lockedin → isolated professional-network fixture; clouddocs → phone Pi document files; mail → Android mock Mail / scoped mail fixture | requires_isolated_source_fixtures |
| lockedin-002 | lockedin → isolated professional-network fixture; dinespot → isolated restaurant service; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-001 | dinespot → isolated restaurant service; teamchat → isolated Mattermost fixture | requires_isolated_source_fixtures |
| multi-002 | skytrip → isolated flight search/history; weather → fixed-date weather service or Android weather controls; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-003 | freshcart → isolated grocery catalog/cart; splitpay → EXCLUDED | excluded_user_scope |
| multi-005 | cityride → isolated ride service; teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture | requires_isolated_source_fixtures |
| multi-006 | dinespot → isolated restaurant service; freshcart → isolated grocery catalog/cart; quickchat → isolated messaging fixture; splitpay → EXCLUDED | excluded_user_scope |
| multi-007 | notes → vivo Notes controls or isolated note store; weather → fixed-date weather service or Android weather controls; skytrip → isolated flight search/history | requires_isolated_source_fixtures |
| multi-008 | caltrack → isolated nutrition store; quickchat → isolated messaging fixture | android_file_adaptation_passed |
| multi-009 | mybank → EXCLUDED; mail → Android mock Mail / scoped mail fixture; notes → vivo Notes controls or isolated note store; quickbite → isolated food-order service | excluded_user_scope |
| multi-010 | megamart → isolated shopping catalog/cart; mybank → EXCLUDED; clouddocs → phone Pi document files | excluded_user_scope |
| multi-011 | stayfinder → isolated lodging search/history; weather → fixed-date weather service or Android weather controls; tasterank → isolated restaurant list; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-012 | trailblaze → isolated activity store; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-013 | clouddocs → phone Pi document files; cloudslides → phone Pi PPTX files; cloudsheets → phone Pi XLSX files | android_file_adaptation_passed |
| multi-014 | cinephile → isolated movie catalog; quickbite → isolated food-order service; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-015 | ticketbox → isolated event catalog/history; cityride → isolated ride service; cloudsheets → phone Pi XLSX files | requires_isolated_source_fixtures |
| multi-016 | splitpay → EXCLUDED; quickchat → isolated messaging fixture | excluded_user_scope |
| multi-017 | clock → Android Clock Intent / scoped clock controls; weather → fixed-date weather service or Android weather controls; trailblaze → isolated activity store; caltrack → isolated nutrition store | requires_isolated_source_fixtures |
| multi-018 | caltrack → isolated nutrition store; freshcart → isolated grocery catalog/cart; notes → vivo Notes controls or isolated note store | android_file_adaptation_passed |
| multi-019 | mybank → EXCLUDED; splitpay → EXCLUDED; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| multi-022 | skytrip → isolated flight search/history; cityride → isolated ride service; clock → Android Clock Intent / scoped clock controls; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-025 | megamart → isolated shopping catalog/cart; clouddocs → phone Pi document files | android_file_adaptation_passed |
| multi-028 | quickbite → isolated food-order service; teamchat → isolated Mattermost fixture | requires_isolated_source_fixtures |
| multi-031 | weather → fixed-date weather service or Android weather controls; clock → Android Clock Intent / scoped clock controls; caltrack → isolated nutrition store; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-032 | teamchat → isolated Mattermost fixture; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files; cloudslides → phone Pi PPTX files | requires_isolated_source_fixtures |
| multi-034 | mybank → EXCLUDED; splitpay → EXCLUDED; megamart → isolated shopping catalog/cart; quickbite → isolated food-order service; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| multi-035 | tasterank → isolated restaurant list; dinespot → isolated restaurant service; cinephile → isolated movie catalog; cityride → isolated ride service; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-036 | clock → Android Clock Intent / scoped clock controls; weather → fixed-date weather service or Android weather controls; caltrack → isolated nutrition store; cityride → isolated ride service; teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture | requires_isolated_source_fixtures |
| multi-037 | teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files; clouddrive → scoped document metadata service; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-039 | caltrack → isolated nutrition store; freshcart → isolated grocery catalog/cart; splitpay → EXCLUDED; notes → vivo Notes controls or isolated note store | excluded_user_scope |
| multi-040 | ticketbox → isolated event catalog/history; cityride → isolated ride service; dinespot → isolated restaurant service; weather → fixed-date weather service or Android weather controls; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-044 | trailblaze → isolated activity store; notes → vivo Notes controls or isolated note store; caltrack → isolated nutrition store; weather → fixed-date weather service or Android weather controls; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-046 | tasterank → isolated restaurant list; dinespot → isolated restaurant service; weather → fixed-date weather service or Android weather controls; splitpay → EXCLUDED; quickchat → isolated messaging fixture | excluded_user_scope |
| multi-048 | clouddrive → scoped document metadata service; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files; cloudslides → phone Pi PPTX files; teamchat → isolated Mattermost fixture | requires_isolated_source_fixtures |
| multi-053 | ticketbox → isolated event catalog/history; dinespot → isolated restaurant service; clouddocs → phone Pi document files; teamchat → isolated Mattermost fixture; quickchat → isolated messaging fixture; cityride → isolated ride service | requires_isolated_source_fixtures |
| multi-055 | clock → Android Clock Intent / scoped clock controls; weather → fixed-date weather service or Android weather controls; caltrack → isolated nutrition store; quickbite → isolated food-order service; freshcart → isolated grocery catalog/cart; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-061 | scorezone → isolated sports feed; cinephile → isolated movie catalog; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-068 | clouddrive → scoped document metadata service; stayfinder → isolated lodging search/history; clouddocs → phone Pi document files; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-069 | cinephile → isolated movie catalog; dinespot → isolated restaurant service; cityride → isolated ride service; clock → Android Clock Intent / scoped clock controls; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-071 | stayfinder → isolated lodging search/history; scorezone → isolated sports feed; ticketbox → isolated event catalog/history; cinephile → isolated movie catalog; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-073 | clock → Android Clock Intent / scoped clock controls; caltrack → isolated nutrition store; quickbite → isolated food-order service; weather → fixed-date weather service or Android weather controls | requires_isolated_source_fixtures |
| multi-074 | splitpay → EXCLUDED; quickchat → isolated messaging fixture; mybank → EXCLUDED | excluded_user_scope |
| multi-075 | trailblaze → isolated activity store; weather → fixed-date weather service or Android weather controls; quickchat → isolated messaging fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-076 | quickbite → isolated food-order service; mail → Android mock Mail / scoped mail fixture; mybank → EXCLUDED | excluded_user_scope |
| multi-077 | skytrip → isolated flight search/history; stayfinder → isolated lodging search/history; teamchat → isolated Mattermost fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-078 | notes → vivo Notes controls or isolated note store; megamart → isolated shopping catalog/cart; mybank → EXCLUDED; quickchat → isolated messaging fixture | excluded_user_scope |
| multi-079 | tasterank → isolated restaurant list; dinespot → isolated restaurant service; weather → fixed-date weather service or Android weather controls; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-080 | caltrack → isolated nutrition store; freshcart → isolated grocery catalog/cart | requires_isolated_source_fixtures |
| multi-081 | teamchat → isolated Mattermost fixture; clouddocs → phone Pi document files; cloudslides → phone Pi PPTX files; clock → Android Clock Intent / scoped clock controls | requires_isolated_source_fixtures |
| multi-083 | mybank → EXCLUDED; splitpay → EXCLUDED; megamart → isolated shopping catalog/cart; clouddocs → phone Pi document files | excluded_user_scope |
| multi-084 | weather → fixed-date weather service or Android weather controls; dinespot → isolated restaurant service; ticketbox → isolated event catalog/history; cityride → isolated ride service; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-085 | teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files; clock → Android Clock Intent / scoped clock controls; weather → fixed-date weather service or Android weather controls; trailblaze → isolated activity store; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| multi-086 | mybank → EXCLUDED; splitpay → EXCLUDED; megamart → isolated shopping catalog/cart; quickbite → isolated food-order service; freshcart → isolated grocery catalog/cart; clouddocs → phone Pi document files; notes → vivo Notes controls or isolated note store; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| multi-087 | skytrip → isolated flight search/history; weather → fixed-date weather service or Android weather controls; notes → vivo Notes controls or isolated note store; teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture; quickchat → isolated messaging fixture; clock → Android Clock Intent / scoped clock controls | requires_isolated_source_fixtures |
| multi-088 | trailblaze → isolated activity store; caltrack → isolated nutrition store; weather → fixed-date weather service or Android weather controls; quickbite → isolated food-order service; quickchat → isolated messaging fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-089 | ticketbox → isolated event catalog/history; cinephile → isolated movie catalog; dinespot → isolated restaurant service; cityride → isolated ride service; splitpay → EXCLUDED; quickchat → isolated messaging fixture | excluded_user_scope |
| multi-090 | clouddrive → scoped document metadata service; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files; cloudslides → phone Pi PPTX files; teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-091 | notes → vivo Notes controls or isolated note store; megamart → isolated shopping catalog/cart; mybank → EXCLUDED; mail → Android mock Mail / scoped mail fixture; quickchat → isolated messaging fixture; splitpay → EXCLUDED | excluded_user_scope |
| multi-092 | lockedin → isolated professional-network fixture; teamchat → isolated Mattermost fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| multi-093 | lockedin → isolated professional-network fixture; quickchat → isolated messaging fixture; clock → Android Clock Intent / scoped clock controls | requires_isolated_source_fixtures |
| mem-001 | splitpay → EXCLUDED; mybank → EXCLUDED | excluded_user_scope |
| mem-002 | cityride → isolated ride service | requires_isolated_source_fixtures |
| mem-003 | quickbite → isolated food-order service | requires_isolated_source_fixtures |
| mem-004 | quickchat → isolated messaging fixture; mail → Android mock Mail / scoped mail fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-005 | trailblaze → isolated activity store; weather → fixed-date weather service or Android weather controls; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| mem-006 | caltrack → isolated nutrition store; freshcart → isolated grocery catalog/cart | requires_isolated_source_fixtures |
| mem-007 | scorezone → isolated sports feed; ticketbox → isolated event catalog/history | requires_isolated_source_fixtures |
| mem-009 | skytrip → isolated flight search/history; quickchat → isolated messaging fixture; tasterank → isolated restaurant list | requires_isolated_source_fixtures |
| mem-010 | notes → vivo Notes controls or isolated note store; mail → Android mock Mail / scoped mail fixture; clock → Android Clock Intent / scoped clock controls; clouddocs → phone Pi document files | requires_isolated_source_fixtures |
| mem-013 | notes → vivo Notes controls or isolated note store; quickchat → isolated messaging fixture; mail → Android mock Mail / scoped mail fixture; megamart → isolated shopping catalog/cart; dinespot → isolated restaurant service | requires_isolated_source_fixtures |
| mem-015 | megamart → isolated shopping catalog/cart; mail → Android mock Mail / scoped mail fixture; ticketbox → isolated event catalog/history | requires_isolated_source_fixtures |
| mem-016 | stayfinder → isolated lodging search/history; tasterank → isolated restaurant list; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-018 | quickbite → isolated food-order service; tasterank → isolated restaurant list; dinespot → isolated restaurant service; ticketbox → isolated event catalog/history; mail → Android mock Mail / scoped mail fixture | requires_isolated_source_fixtures |
| mem-019 | mybank → EXCLUDED; splitpay → EXCLUDED; megamart → isolated shopping catalog/cart; freshcart → isolated grocery catalog/cart; clouddocs → phone Pi document files | excluded_user_scope |
| mem-020 | scorezone → isolated sports feed; quickchat → isolated messaging fixture; splitpay → EXCLUDED; freshcart → isolated grocery catalog/cart; ticketbox → isolated event catalog/history | excluded_user_scope |
| mem-021 | cityride → isolated ride service; mybank → EXCLUDED; clouddocs → phone Pi document files; mail → Android mock Mail / scoped mail fixture; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| mem-022 | quickchat → isolated messaging fixture; splitpay → EXCLUDED; teamchat → isolated Mattermost fixture; tasterank → isolated restaurant list | excluded_user_scope |
| mem-023 | megamart → isolated shopping catalog/cart; mybank → EXCLUDED; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| mem-024 | cinephile → isolated movie catalog; ticketbox → isolated event catalog/history; scorezone → isolated sports feed; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| mem-025 | skytrip → isolated flight search/history; stayfinder → isolated lodging search/history; cityride → isolated ride service; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files | requires_isolated_source_fixtures |
| mem-026 | teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-027 | trailblaze → isolated activity store; caltrack → isolated nutrition store; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-028 | mybank → EXCLUDED; splitpay → EXCLUDED; megamart → isolated shopping catalog/cart; freshcart → isolated grocery catalog/cart; quickbite → isolated food-order service; cityride → isolated ride service; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| mem-029 | tasterank → isolated restaurant list; dinespot → isolated restaurant service; quickbite → isolated food-order service; freshcart → isolated grocery catalog/cart; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-030 | clock → Android Clock Intent / scoped clock controls; trailblaze → isolated activity store; quickbite → isolated food-order service; mail → Android mock Mail / scoped mail fixture; quickchat → isolated messaging fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-031 | notes → vivo Notes controls or isolated note store; skytrip → isolated flight search/history; stayfinder → isolated lodging search/history; ticketbox → isolated event catalog/history; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files; clock → Android Clock Intent / scoped clock controls | requires_isolated_source_fixtures |
| mem-032 | quickchat → isolated messaging fixture; splitpay → EXCLUDED; trailblaze → isolated activity store; tasterank → isolated restaurant list | excluded_user_scope |
| mem-033 | mail → Android mock Mail / scoped mail fixture; mybank → EXCLUDED; megamart → isolated shopping catalog/cart; notes → vivo Notes controls or isolated note store; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| mem-034 | skytrip → isolated flight search/history; cityride → isolated ride service; mybank → EXCLUDED; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files | excluded_user_scope |
| mem-035 | ticketbox → isolated event catalog/history; scorezone → isolated sports feed; megamart → isolated shopping catalog/cart; mybank → EXCLUDED; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files | excluded_user_scope |
| mem-036 | trailblaze → isolated activity store; caltrack → isolated nutrition store; freshcart → isolated grocery catalog/cart; notes → vivo Notes controls or isolated note store; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| mem-037 | quickchat → isolated messaging fixture; splitpay → EXCLUDED; dinespot → isolated restaurant service; quickbite → isolated food-order service; ticketbox → isolated event catalog/history; clouddocs → phone Pi document files | excluded_user_scope |
| mem-039 | stayfinder → isolated lodging search/history; skytrip → isolated flight search/history; tasterank → isolated restaurant list; dinespot → isolated restaurant service; weather → fixed-date weather service or Android weather controls; cloudsheets → phone Pi XLSX files; notes → vivo Notes controls or isolated note store; quickchat → isolated messaging fixture; clouddocs → phone Pi document files | requires_isolated_source_fixtures |
| mem-040 | mybank → EXCLUDED; trailblaze → isolated activity store; caltrack → isolated nutrition store; teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture; quickchat → isolated messaging fixture; splitpay → EXCLUDED; scorezone → isolated sports feed; clouddocs → phone Pi document files | excluded_user_scope |
| mem-041 | notes → vivo Notes controls or isolated note store; quickchat → isolated messaging fixture | requires_isolated_source_fixtures |
| mem-042 | clouddrive → scoped document metadata service; clouddocs → phone Pi document files; cloudsheets → phone Pi XLSX files; cloudslides → phone Pi PPTX files | requires_isolated_source_fixtures |
| mem-043 | stayfinder → isolated lodging search/history; scorezone → isolated sports feed; ticketbox → isolated event catalog/history | requires_isolated_source_fixtures |
| mem-044 | clock → Android Clock Intent / scoped clock controls; trailblaze → isolated activity store; mail → Android mock Mail / scoped mail fixture; teamchat → isolated Mattermost fixture; caltrack → isolated nutrition store; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-045 | cloudslides → phone Pi PPTX files; clouddrive → scoped document metadata service; teamchat → isolated Mattermost fixture; clouddocs → phone Pi document files | requires_isolated_source_fixtures |
| mem-046 | cityride → isolated ride service; mail → Android mock Mail / scoped mail fixture; mybank → EXCLUDED; clouddocs → phone Pi document files | excluded_user_scope |
| mem-047 | mybank → EXCLUDED; splitpay → EXCLUDED; quickbite → isolated food-order service; megamart → isolated shopping catalog/cart; freshcart → isolated grocery catalog/cart; notes → vivo Notes controls or isolated note store | excluded_user_scope |
| mem-048 | teamchat → isolated Mattermost fixture; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files; quickchat → isolated messaging fixture; notes → vivo Notes controls or isolated note store; lockedin → isolated professional-network fixture | requires_isolated_source_fixtures |
| mem-049 | trailblaze → isolated activity store; quickchat → isolated messaging fixture; splitpay → EXCLUDED; weather → fixed-date weather service or Android weather controls; notes → vivo Notes controls or isolated note store; lockedin → isolated professional-network fixture | excluded_user_scope |
| mem-050 | notes → vivo Notes controls or isolated note store; quickchat → isolated messaging fixture; megamart → isolated shopping catalog/cart; mail → Android mock Mail / scoped mail fixture; clouddocs → phone Pi document files | requires_isolated_source_fixtures |
| mem-051 | lockedin → isolated professional-network fixture; mail → Android mock Mail / scoped mail fixture; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
| mem-052 | lockedin → isolated professional-network fixture; teamchat → isolated Mattermost fixture; clouddocs → phone Pi document files; notes → vivo Notes controls or isolated note store | requires_isolated_source_fixtures |
