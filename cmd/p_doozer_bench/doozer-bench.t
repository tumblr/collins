
{{ define "start-doozerd-leader" }}
mkdir /home/petar/var
mkdir /home/petar/var/doozer-bench
setenv GOMAXPROCS 3
/home/petar/doozer-bench.devbox/doozerd -l {{.Bind}}:8040 -w :8080 >& /home/petar/var/doozer-bench/doozerd &
{{ end }}

{{ define "start-doozerd-slave" }}
mkdir /home/petar/var
mkdir /home/petar/var/doozer-bench
setenv GOMAXPROCS 3
/home/petar/doozer-bench.devbox/doozerd -l {{.Bind}}:8040 -w :8080 -a {{.Leader}}:8040 >& /home/petar/var/doozer-bench/doozerd &
{{ end }}

{{ define "stop-doozerd" }}
killall doozerd
{{ end }}

{{ define "start-bench" }}
setenv GOMAXPROCS 1
/home/petar/doozer-bench.devbox/p_doozer_bench -doozerd {{.Doozerd}}:8040 -k 50 -n 100 -id {{.Id}} -twin {{.Twin}}  >& /home/petar/var/doozer-bench/p_doozer_bench &
{{ end }}
