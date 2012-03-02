#!/bin/sh

charArray=( 'a' 'b' 'c' 'd' 'e' 'f' 'g' 'h' 'i' 'j' 'k' 'l' 'm' 'n' 'o' 'p' 'q' 'r' 's' 't' 'u' 'v' 'w' 'x' 'y' 'z' 'A' 'B' 'C' 'D' 'E' 'F' 'G' 'H' 'I' 'J' 'K' 'L' 'M' 'N' 'O' 'P' 'Q' 'R' 'S' 'T' 'U' 'V' 'W' 'X' 'Y' 'Z' )
numArray=( 0 1 2 3 4 5 6 7 8 9 )

password=''
function get_pass() { 
	length=$1
	password=''
	for (( x=0;x<$length;x++ )); do
		type=$((RANDOM%2))
		if [ $type -eq 0 ]; then
			char=$((RANDOM%52))
			newChar=${charArray[$char]}
		elif [ $type -eq 1 ]; then
			char=$((RANDOM%10))
			newChar=${numArray[$char]}
		fi
		password=$password$newChar
	done
}

FILE="htpasswd_file"
read names # take names from stdin

if [ -z "$names" ];
then
    echo "no names provided.  pipe some in.  ex: echo \"me you theotherguy\" | $0"
    exit 1
fi

for username in $names; do
	get_pass 12
	echo "$username - $password"
	FILE_V=`htpasswd -n -b -s $username $password`
	echo "${FILE_V}:infra" >> $FILE
done

echo "done.  passwords in $FILE"
