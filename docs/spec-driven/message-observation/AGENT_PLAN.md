# Agent ownership

User explicitly requested a UI subagent, then immediate app integration.

- `stitch_ui`: CaseListScreen, new workspace navigation, UI components/type/resources/privacy semantics tests. Preserve incumbent uncommitted work. Main owns navigation wiring.
- `notification_ui`: new observation screen/import mapping, strings_observation and mapping tests. Main owns collector lifetime, alerts, build and permission grants.
- `accessibility_capture`: dedicated new acquisition/accessibility module and settings.gradle inclusion only. Independent implementation; no AgentHita code reuse.
- Main: integration, capture screen, vault vocabulary, snapshot analysis boundary, policy exception, documentation and final checks/device evidence.

No commits, pushes, model downloads or credential files in this task. Shared dirty files remain in the owner's working tree; no reset or overwrite of unrelated changes.
