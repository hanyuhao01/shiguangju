var exec = require('cordova/exec');

var FloatingWindow = {
    /**
     * 检测当前是否能显示悬浮窗（SYSTEM_ALERT_WINDOW 是否已授权）。
     * cb(true/false)
     */
    canShow: function (cb) { exec(cb, function (err) { (cb || function () {})(false); }, 'FloatingWindow', 'canShow', []); },

    /**
     * 请求显示悬浮窗。
     * - 若已授权：直接显示
     * - 若未授权：跳转系统"显示在其他应用上层"设置页，用户授权后需再次调用 requestShow
     *   （红米/澎湃OS 需在设置页选「始终允许」，并额外开启「后台弹出界面」）
     */
    requestShow: function (cb) {
        var ok = function (granted) {
            if (granted) {
                exec(function () { (cb || function () {})(true); }, function () { (cb || function () {})(false); }, 'FloatingWindow', 'requestShow', []);
            } else {
                // 未授权：跳转设置页引导用户开启
                FloatingWindow._openOverlaySettings(function () {
                    (cb || function () {})(false);
                });
            }
        };
        // 先查询权限状态
        exec(ok, function () { (cb || function () {})(false); }, 'FloatingWindow', 'canShow', []);
    },

    /** 隐藏悬浮窗 */
    hide: function (cb) { exec(function () { (cb || function () {})(true); }, function () { (cb || function () {})(false); }, 'FloatingWindow', 'hide', []); },

    /** 内部：跳转 SYSTEM_ALERT_WINDOW 设置页（仅 Android） */
    _openOverlaySettings: function (cb) {
        exec(function () { (cb || function () {})(true); }, function () { (cb || function () {})(false); }, 'FloatingWindow', 'openOverlaySettings', []);
    },

    // ===== 新增方法1：主界面每秒调用，把时间推给悬浮窗 =====
    /**
     * 更新悬浮窗上的数字
     * @param {string} time   格式 "HH:MM:SS"，如 "00:00:03"
     * @param {boolean} running  是否计时中
     * @param {function} cb  回调(可选)
     */
    updateTime: function (time, running, cb) {
        exec(
            function () { (cb || function () {})(true); },
            function () { (cb || function () {})(false); },
            'FloatingWindow', 'updateTime', [time, !!running]
        );
    },

    // ===== 新增方法2：注册"播放/暂停"按钮回调 =====
    /**
     * 监听悬浮窗上的播放/暂停按钮点击
     * @param {function} cb  回调，点击时触发 cb({action:'toggle'})
     */
    onToggle: function (cb) {
        exec(cb, function () {}, 'FloatingWindow', 'onToggle', []);
    }
};

module.exports = FloatingWindow;
