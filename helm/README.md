# Helm Chart: user-management

Helm chart for deploying the User Management Backend on Kubernetes.

---

## Chart Structure

```
helm/user-management/
├── Chart.yaml                          # Chart metadata (name, version, appVersion)
├── values.yaml                         # Default values (dev/local)
├── values-staging.yaml                 # Staging environment overrides
├── values-prod.yaml                    # Production environment overrides
└── templates/
    ├── _helpers.tpl                    # Reusable helpers (labels, names, namespace)
    ├── deployment.yaml                 # Spring Boot app deployment
    ├── service.yaml                    # App service (LoadBalancer)
    ├── configmap.yaml                  # App configuration
    ├── secret.yaml                     # Sensitive credentials
    ├── hpa.yaml                        # Horizontal Pod Autoscaler (conditional)
    ├── mysql.yaml                      # MySQL deployment + service
    ├── rabbitmq.yaml                   # RabbitMQ deployment + service
    └── monitoring/
        ├── prometheus-configmap.yaml   # Prometheus scrape configuration
        ├── prometheus-deployment.yaml  # Prometheus (Deployment + RBAC + Service)
        └── grafana-deployment.yaml     # Grafana (Deployment + Datasource + Service)
```

## What Gets Deployed

### production namespace
- **backend** — Spring Boot app (replicas controlled by `replicaCount`)
- **mysql** — MySQL 8.0 database
- **rabbitmq** — RabbitMQ 3 with management plugin
- **backend-hpa** — Horizontal Pod Autoscaler (when `hpa.enabled: true`)

### monitoring namespace (created automatically)
- **prometheus** — Metrics collection, scrapes backend at `/actuator/prometheus`
- **grafana** — Dashboard UI, auto-provisioned with Prometheus datasource

---

## Quick Start (Local with Minikube)

### Prerequisites

```bash
brew install helm minikube
```

### Start Cluster

```bash
minikube start --driver=docker --memory=4096 --cpus=2
```

### Build Image Inside Minikube

```bash
eval $(minikube docker-env)
docker build -t neeraj06092024/user-management-backend:latest .
```

### Deploy

```bash
kubectl create namespace production

helm upgrade --install user-mgmt ./helm/user-management \
  --namespace production \
  --set image.pullPolicy=Never
```

### Access the App

```bash
kubectl port-forward svc/backend-service 9090:8080 -n production
# App available at http://localhost:9090
```

### Verify

```bash
curl http://localhost:9090/actuator/health
```

---

## Deploy to Different Environments

```bash
# Dev (default values.yaml)
helm upgrade --install user-mgmt ./helm/user-management -n production

# Staging
helm upgrade --install user-mgmt ./helm/user-management \
  -f helm/user-management/values-staging.yaml

# Production
helm upgrade --install user-mgmt ./helm/user-management \
  -f helm/user-management/values-prod.yaml

# CI/CD with dynamic image tag and secrets
helm upgrade --install user-mgmt ./helm/user-management \
  -f helm/user-management/values-prod.yaml \
  --set image.tag=$GIT_SHA \
  --set secrets.dbPassword=$DB_PASSWORD \
  --set secrets.awsAccessKey=$AWS_ACCESS_KEY \
  --set secrets.awsSecretKey=$AWS_SECRET_KEY
```

---

## Configuration Reference

### values.yaml — Key Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `replicaCount` | 1 | Number of backend replicas |
| `image.repository` | neeraj06092024/user-management-backend | Docker image |
| `image.tag` | latest | Image tag |
| `image.pullPolicy` | Always | Image pull policy (set to `Never` for Minikube) |
| `namespace` | production | Target namespace |
| `service.type` | LoadBalancer | Service type |
| `service.port` | 8080 | Service port |

### Application Config (ConfigMap)

| Parameter | Default |
|-----------|---------|
| `config.datasourceUrl` | jdbc:mysql://mysql:3306/users |
| `config.datasourceUsername` | root |
| `config.hibernateDdlAuto` | update |
| `config.hibernateDialect` | org.hibernate.dialect.MySQL8Dialect |
| `config.jwtExpiration` | 86400000 |
| `config.s3BucketName` | user-123-management |
| `config.s3Region` | eu-north-1 |
| `config.corsAllowedOrigins` | http://localhost:4200 |
| `config.rabbitmqHost` | rabbitmq |
| `config.rabbitmqPort` | 5672 |
| `config.rabbitmqUsername` | admin |
| `config.rabbitmqPassword` | password |

### Secrets

| Parameter | Default | Notes |
|-----------|---------|-------|
| `secrets.dbPassword` | root1234 | Override in production |
| `secrets.jwtSecret` | (base64 key) | Override in production |
| `secrets.awsAccessKey` | dummy-access-key | Replace with real key |
| `secrets.awsSecretKey` | dummy-secret-key | Replace with real key |

### HPA (Horizontal Pod Autoscaler)

| Parameter | Default |
|-----------|---------|
| `hpa.enabled` | true |
| `hpa.minReplicas` | 1 |
| `hpa.maxReplicas` | 5 |
| `hpa.cpuUtilization` | 70 |
| `hpa.memoryUtilization` | 80 |

### Monitoring

| Parameter | Default |
|-----------|---------|
| `monitoring.prometheus.enabled` | true |
| `monitoring.prometheus.image` | prom/prometheus:v2.51.0 |
| `monitoring.prometheus.retention` | 15d |
| `monitoring.grafana.enabled` | true |
| `monitoring.grafana.image` | grafana/grafana:10.4.2 |
| `monitoring.grafana.adminUser` | admin |
| `monitoring.grafana.adminPassword` | admin |

### Resource Limits

| Parameter | Default |
|-----------|---------|
| `resources.requests.memory` | 512Mi |
| `resources.requests.cpu` | 250m |
| `resources.limits.memory` | 1Gi |
| `resources.limits.cpu` | 500m |

---

## Common Operations

```bash
# Check deployed pods
kubectl get pods -n production
kubectl get pods -n monitoring

# View backend logs
kubectl logs -n production -l app=backend -f

# Check Helm releases
helm list -n production

# Dry run (preview manifests)
helm template user-mgmt ./helm/user-management

# Lint chart
helm lint ./helm/user-management

# Uninstall
helm uninstall user-mgmt -n production

# Rebuild image after code changes (Minikube)
eval $(minikube docker-env)
docker build -t neeraj06092024/user-management-backend:latest .
kubectl rollout restart deployment/backend -n production
```

---

## Environment Overrides

### values-staging.yaml
- 2 replicas, staging DB URL, staging CORS origin
- HPA: min 2, max 5

### values-prod.yaml
- 3 replicas, production RDS endpoint, production CORS origin
- `hibernateDdlAuto: validate` (no auto schema changes)
- HPA: min 3, max 10
- Grafana admin password changed from default
