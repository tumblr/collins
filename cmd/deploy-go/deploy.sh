#!/bin/sh

# copy Go distribution to remote
scp go.go1.linux-amd64.tar.gz $1:

# perform deployment instructions remotely
ssh $1 'bash -s' < remote_deploy.sh
