
{{ define "launch_doozerd_leader" }}
mkdir /home/petar/var
mkdir /home/petar/var/doozer-bench
setenv GOMAXPROCS 3
/home/petar/doozer-bench.devbox/doozerd -l {{ .Bind }}:8040 -w :8080 >& /home/petar/var/doozer-bench/doozerd &
{{ end }}

{{ define "launch_doozerd_slave" }}
mkdir /home/petar/var
mkdir /home/petar/var/doozer-bench
setenv GOMAXPROCS 3
/home/petar/doozer-bench.devbox/doozerd -l {{ .Bind }}:8040 -w :8080 -a {{ .Leader }}:8040 >& /home/petar/var/doozer-bench/doozerd &
{{ end }}
