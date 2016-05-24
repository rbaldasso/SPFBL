#!/bin/bash
# chkconfig: 345 99 01
# description: SPFBL
##########################################
#       Gerenciador de start / stop      #                  
#               SPFBL                    #
##########################################
if [ -f /etc/lsb-release ]; then
  . /etc/lsb-release
else
  . /etc/init.d/functions
fi

start()
{
    ret=0
    echo -n "[SPFBL] Starting... "

    if [ "$(ps auxwf | grep java | grep SPFBL | grep -v grep | wc -l)" -eq "0" ]; then
        # Log personalizado caso nao deseja utilizar logrotate.d/spfbl
        if [ ! -f /etc/logrotate.d/spfbl ]; then
            /usr/bin/java -jar /opt/spfbl/SPFBL.jar 9875 512 >> /var/log/spfbl/spfbl-$(date "+%Y-%m-%d").log &
        else
            /usr/bin/java -jar /opt/spfbl/SPFBL.jar 9875 512 >> /var/log/spfbl/spfbl.log &
        fi
        sleep 5
        ret=$(ps auxwf | grep java | grep SPFBL | grep -v grep | wc -l)
        if [ "$ret" -eq "0" ]; then
            echo -n "Error"
        fi
    else
       echo -n "Already started. "
       ret=1
    fi
    [ "$ret" -eq "1" ] && success || failure
    echo
}

stop()
{
    ret=0
    echo -n "[SPFBL] Stopping... "
    if [ "$(ps auxwf | grep java | grep SPFBL | grep -v grep | wc -l)" -eq "1" ]; then
        response=$(echo "SHUTDOWN" | nc 127.0.0.1 9875)
        sleep 5
        if [[ $response == "" ]]; then
            # Encerro o processo via kill pois certamente esta trancado
            pid=$(ps aux | grep SPFBL | grep java | grep -v grep | awk '{print $2}')
            kill -9 $pid
            ret=0
        elif [[ $response == "OK" ]]; then
            ret=0
        fi
    else
       echo -n "Already stopped. "
       ret=1
    fi
    [ "$ret" -eq "0" ] && success || failure
    echo
}

restart()
{
    stop
    start
}

status()
{
    if [ "$(ps auxwf | grep java | grep SPFBL | grep -v grep | wc -l)" -eq "1" ]; then
        echo -n "[SPFBL] Server is running"
        echo
        ps axwuf | grep -E "PID|SPFBL" | grep -v grep
    else
        echo -n "[SPFBL] Server is not running"
        echo
    fi
}

case "$1" in
    start)
        start
    ;;
    stop)
        stop
    ;;
    restart)
        restart
    ;;
    status)
        status
    ;;
esac
