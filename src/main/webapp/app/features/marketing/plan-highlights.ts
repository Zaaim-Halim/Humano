import { PublicFeature, PublicPlan, PublicPricing } from './marketing.service';

/** One line of a plan's card: a feature the plan includes, and how. */
export interface PlanHighlight {
  feature: PublicFeature;
  item: PublicPlan['items'][number];
}

/**
 * What a plan's card lists, in order: everything the first plan includes, then for each other plan what
 * it adds or raises over the previous one. Catalog order, except that the headline limit (the first
 * counted feature: employees) leads and the other raised limits come last — so when a card shows only a
 * few lines, the modules a plan adds (payroll, performance reviews…) are not crowded out by limits.
 * Shared by the pricing page and the plan picker in billing settings.
 */
export function planHighlights(pricing: PublicPricing, index: number): PlanHighlight[] {
  const changed = planAdditions(pricing, index);
  const limits = changed.filter(h => h.feature.limitType === 'QUANTITY');
  const others = changed.filter(h => h.feature.limitType !== 'QUANTITY');
  return [...limits.slice(0, 1), ...others, ...limits.slice(1)];
}

/**
 * Everything the first plan includes, or what another plan adds or raises over the previous one, in
 * catalog order: the platform console's plan cards. The plan picker in billing settings lists the same.
 */
export function planAdditions(pricing: PublicPricing, index: number): PlanHighlight[] {
  const catalog = new Map(pricing.features.map((feature, order) => [feature.id, { feature, order }]));
  const plan = pricing.plans[index];
  const previous = index > 0 ? pricing.plans[index - 1] : null;
  const key = (it: PublicPlan['items'][number]): string => `${it.limitValue}|${it.note}`;
  const before = new Map((previous?.items ?? []).filter(it => it.included).map(it => [it.featureId, key(it)]));
  return plan.items
    .filter(it => it.included && catalog.has(it.featureId) && before.get(it.featureId) !== key(it))
    .sort((a, b) => catalog.get(a.featureId)!.order - catalog.get(b.featureId)!.order)
    .map(item => ({ feature: catalog.get(item.featureId)!.feature, item }));
}
