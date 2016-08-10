#!/bin/bash
##########################################
#       Monitor do sistema SPFBL		 #                  
#        autoria: NOC LHOST              #
##########################################

#!/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin

#ps auxw | grep apache2 | grep -v grep > /dev/null
#lsof -i :80 | grep httpd | grep -v grep > /dev/null
#curl -L http://loophost.com.br

verificaWeb(){ 

    hostname=$(cat /etc/spfbl/spfbl.conf | grep hostname= | cut -d'=' -f 2)
    porta=$(cat /etc/spfbl/spfbl.conf | grep http_port | cut -d'=' -f 2)
    verifica=$(curl -s -o /dev/null -w "%{http_code}" $hostname:$porta)
    if [ $verifica != 200 ]
    then
        reiniciaServico
    fi
}

verificaRespostas(){ 

    consulta=$(spfbl check 127.0.0.1 root localhost.localdomain)
    
    if [ $consulta != LAN ]
    then
        reiniciaServico
    fi
    elif [ $consulta == LAN ]
    then
        consulta2=$(spfbl query "200.160.7.130" "gter-bounces@eng.registro.br" "eng.registro.br" "destinatario@destino.com.br")
        sleep 2
        if [ $consulta2 != PASS ]
        then
            reiniciaServico
        fi
    fi
    
}

reiniciaServico(){
        /etc/init.d/spfbl restart > /dev/null
        sleep 10
    
        if [ "$(ps auxwf | grep java | grep SPFBL | grep -v grep | wc -l)" -eq "1" ]; then
            kill $(ps aux | grep SPFBL | grep java | grep -v grep | awk '{print $2}')
        fi   
}
