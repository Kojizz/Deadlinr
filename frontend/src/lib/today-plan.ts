import type {WorkItem} from "./work";
import type {components} from "./api.d";

export type DeadlineRisk=components["schemas"]["DeadlineRisk"];
export type PlanningItem=Omit<components["schemas"]["PlanningItem"],"task">&{task:WorkItem;};
export type TodayPlan=Omit<components["schemas"]["TodayPlan"],"items">&{items:PlanningItem[];};
export const riskReasons:Record<string,string>={
 OVERDUE:"The deadline has passed.",DUE_TODAY:"The deadline is today in your viewing timezone.",
 DUE_SOON:"The deadline is within 72 hours.",BLOCKED:"An unfinished dependency blocks this task.",
 HARD_DEADLINE:"This is a fixed deadline.",
};
