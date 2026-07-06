# Build stage
FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Runtime stage
FROM nginx:alpine

# Install gettext for envsubst
RUN apk add --no-cache gettext

# Copy nginx config as a template
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Copy built frontend
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

# envsubst replaces ${BACKEND_URL} in the nginx template before nginx starts
CMD ["/bin/sh", "-c", "envsubst '${BACKEND_URL}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]