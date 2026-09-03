# Multi-stage production build for OpenTV Remote Pairing Service
FROM node:18-alpine AS base
WORKDIR /app
RUN apk add --no-cache dumb-init

FROM base AS dependencies
COPY package*.json ./
RUN npm ci --only=production && npm cache clean --force

FROM base AS runner
ENV NODE_ENV=production
ENV PORT=3000

WORKDIR /app

# Run as unprivileged node user for security
USER node

# Copy production node_modules
COPY --chown=node:node --from=dependencies /app/node_modules ./node_modules
COPY --chown=node:node package*.json ./
COPY --chown=node:node server.js ./
COPY --chown=node:node public ./public

EXPOSE 3000

# Use dumb-init to properly handle PID 1 signals (SIGTERM, SIGINT) in Alpine
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["node", "server.js"]
