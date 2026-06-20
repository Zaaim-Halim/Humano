// Actions
export { ButtonComponent } from './button/button.component';
export type { ButtonVariant, ButtonSize } from './button/button.component';
export { IconButtonComponent } from './button/icon-button.component';

// Data display
export { AvatarComponent, AvatarGroupComponent } from './data-display/avatar.component';
export type { AvatarSize, Person } from './data-display/avatar.component';
export { BadgeComponent } from './data-display/badge.component';
export { TagComponent } from './data-display/tag.component';
export { CardComponent } from './data-display/card.component';
export { ProgressComponent } from './data-display/progress.component';
export type { ProgressTone } from './data-display/progress.component';
export { StatTileComponent, SparklineComponent } from './data-display/stat-tile.component';
export type { TrendDirection, SparklineTone } from './data-display/stat-tile.component';

// Feedback
export { AlertComponent, ToastComponent, ToneIconComponent } from './feedback/alert.component';
export type { FeedbackTone } from './feedback/alert.component';
export { DialogComponent, DrawerComponent } from './feedback/dialog.component';
export { EmptyStateComponent, SkeletonComponent, SkeletonRowComponent } from './feedback/empty-state.component';
export { TooltipComponent } from './feedback/tooltip.component';
export type { TooltipSide } from './feedback/tooltip.component';
export { ToastService } from './feedback/toast.service';
export type { ToastInstance, ToastOptions } from './feedback/toast.service';
export { ToastHostComponent } from './feedback/toast-host.component';

// Forms
export { InputComponent, TextareaComponent } from './forms/input.component';
export { SelectComponent } from './forms/select.component';
export type { SelectOption } from './forms/select.component';
export { AutocompleteComponent } from './forms/autocomplete.component';
export type { AutocompleteOption } from './forms/autocomplete.component';
export { CheckboxComponent, RadioComponent } from './forms/checkbox.component';
export { SwitchComponent } from './forms/switch.component';
export { FormFieldComponent } from './forms/form-field.component';

// Navigation
export { TabsComponent } from './navigation/tabs.component';
export type { TabItem } from './navigation/tabs.component';
export { BreadcrumbsComponent } from './navigation/breadcrumbs.component';
export type { Crumb } from './navigation/breadcrumbs.component';
export { MenuComponent } from './navigation/menu.component';
export type { MenuItem } from './navigation/menu.component';

// Data / workflow
export { StepperComponent } from './data/stepper.component';
export { DataTableComponent } from './data/data-table.component';
export type { Column, Row, SortState } from './data/data-table.component';

// Shell
export { AppShellComponent } from './shell/app-shell.component';
export type { ShellChrome, NavItem, NavGroup, ShellUser, ShellTenant } from './shell/app-shell.component';
export { PageHeaderComponent } from './shell/page-header.component';
export { CommandPaletteComponent } from './shell/command-palette.component';
export type { Command } from './shell/command-palette.component';
