import { Client } from 'pg'

// Points at the project's own docker-compose Postgres (never Supabase),
// matching CLAUDE.md's "run E2E against local Docker Postgres" rule.
const CONNECTION = {
  host: 'localhost',
  port: 5433,
  database: 'expensewise',
  user: 'dev',
  password: 'devpass',
}

export async function promoteToAdmin(email: string): Promise<void> {
  const client = new Client(CONNECTION)
  await client.connect()
  try {
    await client.query('UPDATE users SET role = $1 WHERE email = $2', ['ADMIN', email])
  } finally {
    await client.end()
  }
}
