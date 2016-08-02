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
verifica=$(curl -s -o /dev/null -w "%{http_code}" http://www.loophost.com.br)

verificaWeb(){ 

    curl -s -o /dev/null -w "%{http_code}" $URLSV:$IPHTTP
}

if [ $verifica != 301 ]
then
        service httpd restart > /dev/null
        service nginx restart > /dev/null
fi
