# Android weather-to-note subcase — 2026-10-30

`li20-weather-two-hours` independently observes two visible hourly forecasts for the current city, then asks the GUI component to write their times, temperatures and conditions into a unique test note. The host reopens that exact note, checks body-region OCR against the source, and deletes only that note. Attempt `REG04e17039c530` passed all three gates: GUI execution, independent acceptance and host cleanup. Its RunLog recorded 11 actions / 47.106 seconds; this single sample is not a reliability or speed benchmark.

Earlier attempt `REGa3ef23f09a83` had a successful GUI terminal but failed host acceptance during an uncertain navigation tap; host cleanup succeeded. The failure is retained. The host now checks the expected destination after an uncertain Back navigation rather than replaying the tap. A separate exploratory run also independently matched the two source lines and had host cleanup, but was not the automated regression attempt.

The scope is two visible hours in the currently displayed city, not LI20's complete Shanghai daily forecast, precipitation, umbrella advice or query timestamp. Source values can change between observation and Agent execution; that correctly fails this comparison. Private source screenshots, observations and notes remain ignored.
