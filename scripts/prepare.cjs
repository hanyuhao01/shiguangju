const fs=require('node:fs');
const test=process.argv.includes('--test');
fs.mkdirSync('www',{recursive:true});
for(const f of ['index.html','core.js','v2.js','v2.css'])fs.copyFileSync(f,'www/'+f);
if(test){let xml=fs.readFileSync('config.xml','utf8').replace('id="com.shiguangju.app"','id="com.shiguangju.test"').replace('<name>拾光橘</name>','<name>拾光橘 V2 测试</name>');fs.writeFileSync('config.xml',xml);}
