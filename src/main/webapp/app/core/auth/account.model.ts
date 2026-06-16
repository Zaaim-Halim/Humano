export class Account {
  constructor(
    public activated: boolean,
    public authorities: string[],
    public email: string,
    public firstName: string | null,
    public langKey: string,
    public lastName: string | null,
    public login: string,
    public imageUrl: string | null,
    // Effective permissions derived from the user's authorities (roles) in the
    // current tenant. Optional for backward compatibility with older payloads.
    public permissions: string[] = [],
  ) {}
}
