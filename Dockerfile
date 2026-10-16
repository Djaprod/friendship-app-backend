FROM node:22-alpine
WORKDIR /app/server
COPY --chown=node:node server/package*.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --chown=node:node server ./
COPY --chown=node:node app/src/main/assets/games /app/app/src/main/assets/games
RUN mkdir -p /app/server/data && chown node:node /app/server/data
USER node
ENV NODE_ENV=production HOST=0.0.0.0 PORT=8787 DATA_DIR=/app/server/data
EXPOSE 8787
CMD ["node", "server.mjs"]
