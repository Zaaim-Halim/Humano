import { PublicFeature, PublicPricing } from './marketing.service';
import { planHighlights } from './plan-highlights';

const feature = (code: string, limitType: PublicFeature['limitType'] = 'BOOLEAN'): PublicFeature => ({
  id: code,
  code,
  name: code,
  description: null,
  category: null,
  limitType,
  unit: null,
});

// Catalog order, as the server sends it.
const FEATURES = [
  feature('EMPLOYEES', 'QUANTITY'),
  feature('ADMIN_SEATS', 'QUANTITY'),
  feature('DIRECTORY'),
  feature('PAYROLL'),
  feature('APPROVAL_CHAINS'),
  feature('PERFORMANCE'),
  feature('SUPPORT'),
];

type PlanItem = PublicPricing['plans'][number]['items'][number];

const item = (featureId: string, limitValue: number | null = null, note: string | null = null): PlanItem => ({
  featureId,
  included: true,
  limitValue,
  note,
});

const plan = (name: string, items: PlanItem[]): PublicPricing['plans'][number] => ({
  id: name,
  type: name,
  name,
  description: null,
  monthlyPrice: 1,
  featured: false,
  items,
});

// Items deliberately out of catalog order: the server does not sort a plan's items.
const PRICING: PublicPricing = {
  currency: 'USD',
  features: FEATURES,
  plans: [
    plan('Starter', [item('SUPPORT', null, 'Email'), item('PAYROLL'), item('ADMIN_SEATS', 3), item('DIRECTORY'), item('EMPLOYEES', 25)]),
    plan('Growth', [
      item('SUPPORT', null, 'Email & chat'),
      item('ADMIN_SEATS', 10),
      item('PERFORMANCE'),
      item('DIRECTORY'),
      item('EMPLOYEES', 150),
      item('PAYROLL'),
      item('APPROVAL_CHAINS'),
    ]),
  ],
};

const codes = (index: number): string[] => planHighlights(PRICING, index).map(h => h.feature.code);

describe('planHighlights', () => {
  it('lists the first plan in catalog order, the headline limit first and other limits last', () => {
    expect(codes(0)).toEqual(['EMPLOYEES', 'DIRECTORY', 'PAYROLL', 'SUPPORT', 'ADMIN_SEATS']);
  });

  it('lists only what a plan adds or raises, with the modules it adds before other limits', () => {
    expect(codes(1)).toEqual(['EMPLOYEES', 'APPROVAL_CHAINS', 'PERFORMANCE', 'SUPPORT', 'ADMIN_SEATS']);
  });

  it('keeps performance reviews on a card that shows only four lines', () => {
    expect(codes(1).slice(0, 4)).toContain('PERFORMANCE');
  });

  it('leaves out features that are not included or not in the catalog', () => {
    const pricing: PublicPricing = {
      ...PRICING,
      plans: [plan('Only', [{ ...item('PAYROLL'), included: false }, item('UNKNOWN'), item('DIRECTORY')])],
    };
    expect(planHighlights(pricing, 0).map(h => h.feature.code)).toEqual(['DIRECTORY']);
  });
});
