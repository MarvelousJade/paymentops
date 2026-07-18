#!/bin/sh
# Render supplies DATABASE_URL as postgres://user:pass@host:port/db; Spring wants jdbc + split creds.
if [ -n "$DATABASE_URL" ]; then
  stripped=${DATABASE_URL#*://}
  creds=${stripped%%@*}
  rest=${stripped#*@}
  export DB_USER=${creds%%:*}
  export DB_PASSWORD=${creds#*:}
  export DB_URL="jdbc:postgresql://${rest}"
fi
exec java -jar app.jar
