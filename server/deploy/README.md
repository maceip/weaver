# Deploying the session bridge to AWS

## Shape

One ECS Fargate service, behind an Application Load Balancer. Each task
owns one Chromium; horizontal scale = more tasks.

```
Android client ──wss──> ALB ──> ECS Fargate task (Chromium + N BrowserContexts)
```

## Build & push the image

```bash
aws ecr create-repository --repository-name weaver-session-bridge
docker build -t weaver-session-bridge server/
docker tag weaver-session-bridge:latest \
  <ACCOUNT>.dkr.ecr.<REGION>.amazonaws.com/weaver-session-bridge:latest
aws ecr get-login-password --region <REGION> \
  | docker login --username AWS --password-stdin <ACCOUNT>.dkr.ecr.<REGION>.amazonaws.com
docker push <ACCOUNT>.dkr.ecr.<REGION>.amazonaws.com/weaver-session-bridge:latest
```

## Task & service

```bash
aws ecs register-task-definition --cli-input-json file://server/deploy/ecs-task-definition.json
aws ecs create-service \
  --cluster weaver \
  --service-name session-bridge \
  --task-definition weaver-session-bridge \
  --desired-count 2 \
  --launch-type FARGATE \
  --load-balancers targetGroupArn=<TG_ARN>,containerName=session-bridge,containerPort=8080
```

## Critical: session affinity

A user's two phones must land on the **same task** — that's where their
`BrowserContext` lives. The shard key is the verified Google `sub`.

The ALB cannot route on a value inside an encrypted id_token, so enable
**target-group stickiness** (duration-based cookie). Two devices on the
same account, opening their WebSockets close in time, will stick to one
task. For deterministic affinity (devices added days apart), put a thin
routing layer in front that hashes the `sub` to a task — or accept that a
cold device may land on a different task and spin up a fresh
`BrowserContext` there (correct, just not shared until both reconnect).

A more robust long-term answer is a Redis-backed `sub -> task` directory
the gateway consults on `hello`; out of scope for this pass.

## TLS

Terminate TLS at the ALB (ACM cert). The container speaks plain HTTP/WS
on 8080 inside the VPC. The Android `RemoteSessionTransport` must use
`wss://`.

## Sizing

2 vCPU / 4 GB per task holds one Chromium plus a few dozen
`BrowserContext`s comfortably. `WEAVER_MAX_CONTEXTS` caps it; past that
the gateway evicts idle contexts and, failing that, rejects new sessions
with `session_unavailable` (the client circuit-breaks to its local
WebView). Watch the `/readyz` `activeContexts` gauge and scale the
service's desired count on it.
