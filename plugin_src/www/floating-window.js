var exec=require('cordova/exec');
function call(op,args,ok,fail){exec(ok||function(){},fail||function(e){console.error('FloatingWindow',op,e);},'FloatingWindow',op,args||[]);}
module.exports={
  canShow:function(cb){call('canShow',[],cb);},
  requestShow:function(cb){call('canShow',[],function(ok){if(ok)call('requestShow',[],cb);else call('openOverlaySettings',[],cb);});},
  hide:function(cb){call('hide',[],cb);},
  openOverlaySettings:function(cb){call('openOverlaySettings',[],cb);},
  state:function(cb,fail){call('state',[],cb,fail);},
  command:function(op,cat,cb,fail){call('command',[op,cat||''],cb,fail);},
  ack:function(ids){call('ack',[ids]);},
  subscribe:function(cb){call('subscribe',[],cb);},
  configure:function(cats,settings){call('configure',[cats,settings]);},
  notificationPermission:function(cb){call('notificationPermission',[],cb);}
  ,exportFile:function(text,name,cb,fail){call('exportFile',[text,name],cb,fail);}
  ,importFile:function(cb,fail){call('importFile',[],cb,fail);}
};
