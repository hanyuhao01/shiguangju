const fs=require('node:fs');
const test=process.argv.includes('--test');
fs.mkdirSync('www',{recursive:true});
for(const f of ['index.html','core.js','v2.js','v2.css','manifest.json','icon-192.png','icon-512.png'])fs.copyFileSync(f,'www/'+f);
if(test){let xml=fs.readFileSync('config.xml','utf8'),version=xml.match(/\bversion="([^"]+)"/)[1],code=xml.match(/android-versionCode="(\d+)"/)[1];xml=xml.replace('id="com.shiguangju.app"','id="com.shiguangju.test.v'+code+'"').replace('<name>拾光橘</name>','<name>拾光橘 V'+version+' 测试</name>');fs.writeFileSync('config.xml',xml);}
