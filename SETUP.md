# Local Development Setup

This document covers the complete setup for running the User Management Backend locally using Docker Compose and on a local Kubernetes cluster using Helm.

---

## Prerequisites

- Docker Desktop
- Homebrew (macOS)

---

## Part 1: Docker Compose (Local Development)

### What It Runs

| Service | Image | Port |
|---------|-------|------|
| Spring Boot App | Built from Dockerfile | 8080 |
| MySQL 8.0 | mysql:8.0 | 3307 (host) -> 3306 (container) |
| RabbitMQ 3 | rabbitmq:3-management | 5672 (AMQP), 15672 (Management UI) |
| Prometheus | prom/prometheus:v2.51.0 | 9090 |
| Grafana | grafana/grafana:10.4.2 | 3000 |

### Steps Taken

1. **Created `docker-compose.yaml`** at project root with MySQL, RabbitMQ, and the Spring Boot app.

2. **Fixed Dockerfile for Apple Silicon** — Changed base images from `alpine` variants to standard ones since `eclipse-temurin:17-jre-alpine` does not support ARM architecture:
   ```
   FROM maven:3.9-eclipse-temurin-17        (was maven:3.9-eclipse-temurin-17-alpine)
   FROM eclipse-temurin:17-jre               (was eclipse-temurin:17-jre-alpine)
   ```

3. **Fixed MySQL port conflict** — Local MySQL was already running on port 3306, so Docker MySQL was mapped to host port 3307. The app container still connects internally on 3306 via `jdbc:mysql://mysql:3306/users`.

4. **Fixed AWS S3 startup crash** — `S3Config.java` throws `NullPointerException` when AWS credentials are blank. Added dummy placeholder values (`dummy-access-key`, `dummy-secret-key`) so the app starts without real AWS keys.

5. **Environment variable override** — All Spring Boot properties are overridden via environment variables in docker-compose (e.g., `SPRING_DATASOURCE_URL`, `SPRING_RABBITMQ_HOST`), so `application.properties` defaults are not used inside Docker.

### How to Run

```bash
# Start App + MySQL + RabbitMQ
docker compose up -d

# Start App + MySQL + RabbitMQ (rebuild after code changes)
docker compose up --build -d

# Start Prometheus + Grafana (separate compose file)
docker compose -f monitoring/docker-compose.yml up -d
```

### How to Stop

```bash
# Stop App + MySQL + RabbitMQ
docker compose down

# Stop Prometheus + Grafana
docker compose -f monitoring/docker-compose.yml down

# Stop and remove all data volumes (full reset)
docker compose down -v
docker compose -f monitoring/docker-compose.yml down -v
```

### Access URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| App | http://localhost:8080 | — |
| Health Check | http://localhost:8080/actuator/health | — |
| Prometheus Metrics | http://localhost:8080/actuator/prometheus | — |
| Prometheus UI | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |
| RabbitMQ UI | http://localhost:15672 | admin / password |
| MySQL | localhost:3307 | root / root1234 |

### AWS S3 Configuration

To enable real S3 functionality, either set environment variables before running:

```bash
export AWS_ACCESS_KEY=your-key
export AWS_SECRET_KEY=your-secret
docker compose up -d
```

Or create a `.env` file in the project root:

```
AWS_ACCESS_KEY=your-key
AWS_SECRET_KEY=your-secret
```

### Monitoring Architecture

```
Spring Boot App (:8080)
    |
    |  /actuator/prometheus (exposes metrics)
    v
Prometheus (:9090)
    |
    |  scrapes every 15s via host.docker.internal:8080
    v
Grafana (:3000)
    |
    |  queries Prometheus at http://prometheus:9090
    |  (auto-provisioned datasource)
    v
Pre-loaded Dashboard
```

Prometheus and Grafana run on a shared Docker network (`monitoring`). Prometheus reaches the app via `host.docker.internal:8080` since the app runs in a separate compose stack.

---

## Part 2: Helm + Minikube (Local Kubernetes)

### What Was Done

1. **Installed Helm** via Homebrew (`brew install helm`).

2. **Created Helm chart** at `helm/user-management/` by templatizing all existing K8s manifests from the `k8s/` directory.

3. **Installed Minikube** via Homebrew (`brew install minikube`).

4. **Started Minikube** with Docker driver:
   ```bash
   minikube start --driver=docker --memory=4096 --cpus=2
   ```

5. **Built Docker image inside Minikube** (so the cluster can access it without a registry):
   ```bash
   eval $(minikube docker-env)
   docker build -t neeraj06092024/user-management-backend:latest .
   ```

6. **Created `production` namespace**:
   ```bash
   kubectl create namespace production
   ```

7. **Deployed via Helm**:
   ```bash
   helm upgrade --install user-mgmt ./helm/user-management \
     --namespace production \
     --set image.pullPolicy=Never
   ```

8. **Fixed S3 crash** — Updated `values.yaml` with dummy AWS credentials.

9. **Added RabbitMQ** — Created `rabbitmq.yaml` template and added RabbitMQ config to the ConfigMap so the app can connect to it inside the cluster.

10. **Upgraded the release** and restarted the backend to pick up new config.

### Helm Chart Structure

```
helm/user-management/
├── Chart.yaml                          # Chart metadata
├── values.yaml                         # Default values (dev/local)
├── values-staging.yaml                 # Staging overrides
├── values-prod.yaml                    # Production overrides
└── templates/
    ├── _helpers.tpl                    # Reusable template helpers (labels, names, namespace)
    ├── deployment.yaml                 # App deployment
    ├── service.yaml                    # App service (LoadBalancer)
    ├── configmap.yaml                  # App config (datasource, RabbitMQ, CORS, JWT, S3)
    ├── secret.yaml                     # App secrets (db password, JWT, AWS keys)
    ├── hpa.yaml                        # Horizontal Pod Autoscaler (conditional)
    ├── mysql.yaml                      # MySQL deployment + service
    ├── rabbitmq.yaml                   # RabbitMQ deployment + service
    └── monitoring/
        ├── prometheus-configmap.yaml   # Prometheus scrape config
        ├── prometheus-deployment.yaml  # Prometheus + RBAC + ServiceAccount + Service
        └── grafana-deployment.yaml     # Grafana + Datasource + Dashboard provider + Service
```

### What Gets Deployed

| Pod | Namespace |
|-----|-----------|
| backend | production |
| mysql | production |
| rabbitmq | production |
| prometheus | monitoring |
| grafana | monitoring |

### Key Templatized Values

| Value | Dev Default | Prod Override |
|-------|-------------|---------------|
| `replicaCount` | 1 | 3 |
| `image.tag` | latest | set via CI/CD |
| `config.datasourceUrl` | mysql:3306 | prod-rds-endpoint |
| `config.hibernateDdlAuto` | update | validate |
| `config.corsAllowedOrigins` | localhost:4200 | yourdomain.com |
| `hpa.minReplicas` | 1 | 3 |
| `hpa.maxReplicas` | 5 | 10 |
| `monitoring.prometheus.enabled` | true | true |
| `monitoring.grafana.enabled` | true | true |

### Deploy Commands

```bash
# Dev (default values)
helm upgrade --install user-mgmt ./helm/user-management \
  --namespace production \
  --set image.pullPolicy=Never

# Staging
helm upgrade --install user-mgmt ./helm/user-management \
  -f helm/user-management/values-staging.yaml

# Production
helm upgrade --install user-mgmt ./helm/user-management \
  -f helm/user-management/values-prod.yaml

# Override image tag from CI/CD
helm upgrade --install user-mgmt ./helm/user-management \
  -f helm/user-management/values-prod.yaml \
  --set image.tag=abc123 \
  --set secrets.dbPassword=$DB_PASSWORD

# Preview rendered manifests (dry run)
helm template user-mgmt ./helm/user-management

# Lint chart for errors
helm lint ./helm/user-management
```

### Access Services via Port-Forward

```bash
# Backend app
kubectl port-forward svc/backend-service 9090:8080 -n production
# Access at http://localhost:9090

# Grafana
kubectl port-forward svc/grafana 3000:3000 -n monitoring
# Access at http://localhost:3000

# Prometheus
kubectl port-forward svc/prometheus 9091:9090 -n monitoring
# Access at http://localhost:9091
```

### Manage the Cluster

```bash
# Check all pods
kubectl get pods -n production
kubectl get pods -n monitoring

# Check Helm release
helm list -n production

# View app logs
kubectl logs -n production -l app=backend -f

# Uninstall everything
helm uninstall user-mgmt -n production

# Stop Minikube (preserves state)
minikube stop

# Start Minikube again
minikube start

# Delete Minikube cluster entirely
minikube delete
```

### Important: Rebuilding After Code Changes

Since the Docker image is built inside Minikube's Docker daemon, you must rebuild there:

```bash
# Point Docker CLI to Minikube's Docker
eval $(minikube docker-env)

# Rebuild the image
docker build -t neeraj06092024/user-management-backend:latest .

# Restart the backend pod to pick up new image
kubectl rollout restart deployment/backend -n production
```
