# Resource conflict reasoning subcase, 2026-10-30

An isolated normal-chat adaptation of `MattermostResourceConflictResolutionTask` supplied four fictional same-day bookings and requests, defining touching endpoints as non-conflicting. It asked for a conflict classification and prohibited calendar creation, email, messages and memory writes. The turn used no tools. Its classification matched the supplied intervals: A and D only touch endpoints; B overlaps for 30 minutes and C is contained within an existing booking for 15 minutes. This tests only text reasoning, not Mattermost retrieval, real calendar availability, follow-up messaging or event creation.

The response added an unsolicited alternative for B of 12:00–12:30, although B's original request was 11:30–12:30 (one hour). It did not disclose that the suggested alternative halves the duration. The classification is correct, but the rescheduling suggestion is not a faithful substitute for the original request.
