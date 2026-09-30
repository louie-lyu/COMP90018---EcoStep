package com.ecostep.app.evaluation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ecostep.app.algorithm.DefaultMissionTriggerPlanner
import com.ecostep.app.algorithm.MissionTrigger
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: deterministic local scheduling; no alarms, notifications or wall-clock waits. */
@RunWith(AndroidJUnit4::class)
class MissionTriggerEvaluationTest {
    @Test
    fun evaluateMissionTriggers() = runBlocking {
        val cases = MissionTriggerEvaluationCases.cases()
        val timingCases = cases.filter { it.name in listOf("five_minutes_before_departure",
            "one_millisecond_after_departure", "all_weekdays", "spring_gap_shifted_forward", "autumn_overlap_earlier_offset") }
        LocalEvaluationRun("mission-trigger",
            "Fixed offset-timestamp references for local departure/notification planning, weekday selection and DST. Missing times shift forward; repeated times use the earlier offset. Does not test Android notification delivery or background scheduling.",
            cases.size, timingCases.size).evaluate {
            for (fixture in cases) {
                val expected = if (fixture.rejectsLead) JSONObject().put("error", "IllegalArgumentException") else fixture.expected.toJson()
                case(fixture.name, expected) { actual ->
                    actual.put("zone", fixture.zone).put("now", fixture.now).put("minuteOfDay", fixture.minuteOfDay)
                        .put("leadMinutes", fixture.leadMinutes).put("activeDays", JSONArray(fixture.activeDays.map { it.name }))
                    if (fixture.rejectsLead) {
                        expectIllegalArgument { DefaultMissionTriggerPlanner(fixture.leadMinutes, ZoneId.of(fixture.zone)) }
                        actual.put("error", "IllegalArgumentException")
                    } else {
                        val planner = DefaultMissionTriggerPlanner(fixture.leadMinutes, ZoneId.of(fixture.zone))
                        val trigger = planner.planNextTrigger(fixture.pattern, fixture.currentTimeMillis)
                        actual.put("trigger", trigger.toJson())
                        verify(trigger, fixture)
                    }
                }
            }
            for (fixture in timingCases) {
                val planner = DefaultMissionTriggerPlanner(fixture.leadMinutes, ZoneId.of(fixture.zone))
                val pattern = fixture.pattern
                val now = fixture.currentTimeMillis
                benchmark(fixture.name, pattern.activeDays.size,
                    operation = { planner.planNextTrigger(pattern, now) }, verify = { verify(it, fixture) })
            }
        }
    }

    private fun verify(actual: MissionTrigger?, fixture: MissionTriggerCase) {
        check(actual == fixture.expected) { "Unexpected trigger: $actual; expected ${fixture.expected}" }
        if (actual != null) {
            check(actual.notificationTimeMillis >= fixture.currentTimeMillis) { "Notification is in the past" }
            check(actual.notificationTimeMillis <= actual.expectedDepartureTimeMillis) { "Notification is after departure" }
            val departureDay = Instant.ofEpochMilli(actual.expectedDepartureTimeMillis).atZone(ZoneId.of(fixture.zone)).dayOfWeek
            check(departureDay in fixture.activeDays) { "Departure is on an inactive day" }
        }
    }

    private fun MissionTrigger?.toJson() = JSONObject().put("hasTrigger", this != null)
        .put("departureMillis", this?.expectedDepartureTimeMillis ?: JSONObject.NULL)
        .put("notificationMillis", this?.notificationTimeMillis ?: JSONObject.NULL)
        .put("departureUtc", this?.let { Instant.ofEpochMilli(it.expectedDepartureTimeMillis).toString() } ?: JSONObject.NULL)
        .put("notificationUtc", this?.let { Instant.ofEpochMilli(it.notificationTimeMillis).toString() } ?: JSONObject.NULL)
}
