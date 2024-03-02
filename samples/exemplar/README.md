gradle run

docker run \
    -p 9090:9090 \
    -v ./prometheus.yml:/etc/prometheus/prometheus.yml \
    prom/prometheus
    
curl localhost:8080/coroutine/run/1

seq 1 100000 > events.txt

seq 1 300000 | xargs -P1500 -I{} curl 192.168.66.6:8080/coroutine/run/{}
