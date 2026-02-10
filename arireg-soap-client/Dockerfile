# Build stage
FROM golang:1.21-alpine AS builder

WORKDIR /app

# Copy go files
COPY main.go .

# Build the application
RUN go build -o arireg-soap-client main.go

# Final stage
FROM alpine:latest

RUN apk --no-cache add ca-certificates

WORKDIR /root/

# Copy the binary from builder
COPY --from=builder /app/arireg-soap-client .

# Expose the port
EXPOSE 1236

# Run the application
CMD ["./arireg-soap-client"]
