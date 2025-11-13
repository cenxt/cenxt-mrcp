#!/bin/sh

APP_PATH=${0##*/}
APP_NAME=${APP_PATH%%.*}
bin=`dirname "${BASH_SOURCE-$0}"`
APP_HOME=`cd "$bin"; pwd`

# SET JVM JAVA_OPTS
JAVA_OPTS=-Xms128m
JAVA_OPTS=$JAVA_OPTS' '-Xmx128m
JAVA_OPTS=$JAVA_OPTS' '-XX:MetaspaceSize=64m
JAVA_OPTS=$JAVA_OPTS' '-XX:MaxMetaspaceSize=64m
JAVA_OPTS=$JAVA_OPTS' '-XX:NewSize=32m
JAVA_OPTS=$JAVA_OPTS' '-XX:MaxNewSize=32m
JAVA_OPTS=$JAVA_OPTS' '-XX:+HeapDumpOnOutOfMemoryError
JAVA_OPTS=$JAVA_OPTS' '-XX:HeapDumpPath=/applog/cenxt-mrcp

#******************************************************检查执行用户*****************************************************************#
#USER_ID=`id -u`

#if [ $USER_ID -eq 0 ];then
#	echo "do not use root user for safty";
#	exit 1
#fi
#***********************************************************************************************************************************#


#********************************************************打印系统信息***************************************************************#
#(函数)打印系统环境参数
info()
{
	echo "System Information:"
	echo `/bin/uname`
	echo `uname -a`
	echo "JAVA_HOME=$JAVA_HOME"
	echo "APP_NAME="$APP_NAME
}

psid=0
checkpid()
{
	psid=`ps -ef -eo user,pid,args|grep "$APP_NAME".*jar|grep -v grep|grep -v kill|awk '{print $2}'`
	echo 'pid ='$psid;
}

status()
{
	checkpid
	if [ -n "$psid" ] && [ "$psid" -ne 0 ];  then
		echo "$APP_NAME is running! (pid=$psid)"
	else
 		echo "$APP_NAME is not running"
 	fi
 }

start()
{
	checkpid
	if [ -n "$psid" ] && [ "$psid" -ne 0 ]; then
	  echo "warn: $APP_NAME already started! (pid=$psid)"
	else

	  if [ -n "$1" ] && [ "$1" = "docker" ]; then
	    echo -n "Starting $APP_NAME in docker ..."
	      "$JAVA_HOME"/bin/java -server $JAVA_OPTS -jar "$APP_HOME"/../"$APP_NAME"*.jar --spring.config.location=$APP_HOME/../config/ --spring.profiles.active=${SPRING_PROFILES_ACTIVE} --logging.config=classpath:log4j2.xml
	  else
	    echo -n "Starting $APP_NAME ..."
	      nohup "$JAVA_HOME"/bin/java -server $JAVA_OPTS -jar "$APP_HOME"/../"$APP_NAME"*.jar --spring.config.location=$APP_HOME/../config/ --spring.profiles.active=${SPRING_PROFILES_ACTIVE} --logging.config=classpath:log4j2.xml > /dev/null 2>&1 &
	  fi
	  sleep 3;
	  checkpid
	  if [ -n "$psid" ] && [ "$psid" -ne 0 ]; then
	  	echo "(pid=$psid) [OK]"
	  else
	  	echo "[Failed]"
	  fi
 	fi
 }
#***********************************************************************************************************************************#


#********************************************************停止程序*******************************************************************#
#停止程序
stop()
{
	checkpid
 	if [ -n "$psid" ] && [ "$psid" -ne 0  ]; then
	   	echo -n "Stopping $APP_NAME ...(pid=$psid) "
	    #su - $RUNNING_USER -c "kill -9 $psid"
	    kill -9 $psid
	    if [ "$?" -eq 0 ]; then
	       echo "[OK]"
	    else
	       echo "[Failed]"
	    fi
	       checkpid
	    if [ -n "$psid" ] && [ "$psid" -ne 0 ]; then
	       stop
	    fi
     else
        echo "warn: $APP_NAME is not running"
    fi
}

case "$1" in
	 'start')     start $2   ;;
	 'stop')      stop     ;;
	 'status')    status   ;;
	 'info')      info     ;;
	 *)  echo "Usage:$0 {start|stop|status|info}"     exit 1
esac