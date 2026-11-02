/* Shared offline calculations. Durations are integer milliseconds; intervals are half-open. */
(function(root,factory){if(typeof module==='object')module.exports=factory();else root.SG=factory();})(this,function(){
  'use strict';
  const PALETTE=['#64ffda','#4fc3f7','#b388ff','#f48fb1','#ffb74d','#81c784','#ef5350','#ce93d8'];
  const finite=n=>Number.isFinite(n)&&n>=0;
  const uid=()=>Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);
  const day=t=>{const d=new Date(t);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');};
  const date=s=>new Date(s+'T00:00:00');
  function bounds(type,key){let a=date(key),b=new Date(a);if(type==='week'){a.setDate(a.getDate()-a.getDay());b=new Date(a);b.setDate(b.getDate()+7);}else if(type==='month'){a.setDate(1);b=new Date(a);b.setMonth(b.getMonth()+1);}else if(type==='year'){a=new Date(a.getFullYear(),0,1);b=new Date(a.getFullYear()+1,0,1);}else b.setDate(b.getDate()+1);return [+a,+b];}
  function idle(category){return {schema:2,id:uid(),state:'idle',category:category||'工作',segments:[],active:null,revision:0,reminderElapsed:0};}
  function elapsed(s,now){return s.segments.reduce((n,x)=>n+x.duration,0)+(s.active?Math.max(0,now-s.active.start):0);}
  function transition(input,action,now,category){let s=JSON.parse(JSON.stringify(input));s.revision++;if(action==='start'&&s.state!=='running'){s.state='running';s.active={start:now,category:s.category};}
    if((action==='pause'||action==='save'||action==='category')&&s.active){const duration=Math.max(0,Math.floor(now-s.active.start));s.segments.push({start:s.active.start,end:s.active.start+duration,duration,category:s.active.category});s.reminderElapsed=(s.reminderElapsed||0)+duration;s.active=null;s.state='paused';}
    if(action==='category'){s.category=category;s.reminderElapsed=0;if(input.state==='running'){s.active={start:now,category};s.state='running';}}
    if(action==='reset')return {session:idle(s.category),records:[]};
    if(action==='save'){return {session:idle(s.category),records:sessionRecords(s)};}return {session:s,records:[]};
  }
  function sessionRecords(s){return s.segments.filter(x=>x.duration>0).map((x,i)=>({id:s.id+':'+i,sessionId:s.id,date:day(x.start),start:new Date(x.start).toISOString(),end:new Date(x.end).toISOString(),duration:x.duration,category:x.category,segments:[x]}));}
  function slices(records){const out=[];for(const r of records){
      // Old records do not identify pauses: preserve their exact duration/date, never invent active intervals.
      if(!Array.isArray(r.segments)){if(finite(r.duration)&&/^\d{4}-\d{2}-\d{2}$/.test(r.date))out.push({...r,source:r,legacy:true});continue;}
      for(const x of r.segments){if(!finite(x.duration)||!finite(x.start)||!finite(x.end)||x.end<x.start)continue;let cursor=x.start,left=x.duration;const wall=x.end-x.start;
        while(cursor<x.end){let next=date(day(cursor));next.setDate(next.getDate()+1);let end=Math.min(x.end,+next),n=end===x.end?left:Math.round(x.duration*(end-cursor)/wall);left-=n;out.push({...r,category:x.category||r.category,date:day(cursor),start:new Date(cursor).toISOString(),end:new Date(end).toISOString(),duration:n,source:r});cursor=end;}
      }
    }return out;}
  function range(records,type,key,cats){const [a,b]=bounds(type,key);return slices(records).filter(r=>+date(r.date)>=a&&+date(r.date)<b&&(!cats||cats.includes(r.category)));}
  function aggregate(rows){const cats=Object.create(null);let total=0;for(const r of rows){if(!finite(r.duration))continue;cats[r.category]=(cats[r.category]||0)+r.duration;total+=r.duration;}return {total,cats,percent:Object.fromEntries(Object.entries(cats).map(([k,v])=>[k,total?v/total*100:0]))};}
  function color(name,registry){if(registry[name]&&/^#[0-9a-f]{6}$/i.test(registry[name]))return registry[name];let h=0;for(let i=0;i<name.length;i++)h=((h<<5)-h+name.charCodeAt(i))|0;return registry[name]=PALETTE[Math.abs(h)%PALETTE.length];}
  const duration=n=>{const m=Math.floor(n/60000);return (m>=60?Math.floor(m/60)+'小时':'')+(m%60||m<60?m%60+'分钟':'');};
  function review(records,type,key,goal){const rows=range(records,type,key),s=aggregate(rows),names=Object.keys(s.cats).sort((a,b)=>s.cats[b]-s.cats[a]);if(!s.total)return '这个周期还没有已保存的时间记录，暂时无法分析时间分配。可以先记录一段实际活动，结束后保存；有数据后再生成复盘，避免把没有记录的时间误认为休息或空闲。';
    let text='本'+({day:'日',week:'周',month:'月',year:'年'}[type])+'累计记录'+duration(s.total)+'，'+names.slice(0,2).map(n=>n+duration(s.cats[n])).join('，')+'。'+names[0]+'占'+Math.round(s.percent[names[0]])+'%。';
    const longest=rows.filter(r=>!r.legacy).reduce((n,r)=>Math.max(n,r.duration),0);
    let advice='建议下次开始活动时及时计时，结束后保存，保持记录完整。';
    if(longest>=90*60000)advice='有一段连续活动超过90分钟，建议下一次切换活动时留出几分钟走动或休息。';
    else if(type==='day'&&goal>0){text+='已达到每日目标的'+Math.round(s.total/goal*100)+'%。';advice=s.total>=goal?'目标已达到，建议为后续安排留出休息和临时事务的缓冲。':'建议为尚未完成的目标安排一个明确时段，并给临时事务留出缓冲。';}
    else if(s.percent[names[0]]>=75)advice='时间主要集中在一个类别，建议检查下一周期是否需要为其他已计划的活动预留时段。';
    if(type==='day'){let a=date(key);a.setDate(a.getDate()-7);const prev=slices(records).filter(r=>r.date>=day(a)&&r.date<key),avg=aggregate(prev).total/7;if(avg>0)text+='比前7日平均'+(s.total>=avg?'多':'少')+duration(Math.abs(s.total-avg))+'。';}
    return text+advice;
  }
  const reminderDefaults=()=>({enabled:false,system:false,sound:false,dndStart:'22:00',dndEnd:'08:00',types:{work:{enabled:true,minutes:50},screen:{enabled:true,minutes:20},paper:{enabled:true,minutes:45},sedentary:{enabled:true,minutes:60},exercise:{enabled:false,minutes:60},none:{enabled:false,minutes:60}},categories:{}});
  function quiet(now,start,end){const d=new Date(now),t=d.getHours()*60+d.getMinutes(),p=s=>Number(s.slice(0,2))*60+Number(s.slice(3));const a=p(start),b=p(end);return a===b?false:a<b?t>=a&&t<b:t>=a||t<b;}
  function reminder(s,settings,now,mark){const type=settings.categories[s.category]||'none',rule=settings.types[type];if(!settings.enabled||s.state!=='running'||!s.active||!rule||!rule.enabled||quiet(now,settings.dndStart,settings.dndEnd))return null;const ms=(s.reminderElapsed||0)+Math.max(0,now-s.active.start),interval=rule.minutes*60000;if(ms<interval||ms-(mark||0)<interval)return null;return {type,minutes:rule.minutes,at:now,activeMs:ms};}
  function validateBackup(b){if(!b||b.app!=='shiguangju'||b.schema!==2||!b.storage||typeof b.storage!=='object'||Array.isArray(b.storage)||!Array.isArray(b.images))throw Error('备份格式或版本不支持');
    for(const [k,v] of Object.entries(b.storage)){if(typeof v!=='string'||['__proto__','constructor','prototype'].includes(k))throw Error('无效存储项');JSON.parse(v);}
    const rec=JSON.parse(b.storage.records||'[]');if(!Array.isArray(rec)||rec.some(r=>!finite(r.duration)||typeof r.category!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(r.date)||!Number.isFinite(Date.parse(r.start))||!Number.isFinite(Date.parse(r.end))))throw Error('记录格式不正确');
    for(const key of ['categories','tasks','mascots_meta','review_day','review_week','review_month','review_year'])if(b.storage[key]&&!Array.isArray(JSON.parse(b.storage[key])))throw Error(key+'格式不正确');
    if(b.images.some(i=>!Number.isInteger(i.id)||i.id<1||typeof i.data!=='string'||!/^data:image\/(png|jpeg|gif|webp);base64,/.test(i.data)))throw Error('图片格式不正确');return b;}
  return {uid,day,date,bounds,idle,elapsed,transition,slices,range,aggregate,color,review,reminderDefaults,quiet,reminder,validateBackup};
});
