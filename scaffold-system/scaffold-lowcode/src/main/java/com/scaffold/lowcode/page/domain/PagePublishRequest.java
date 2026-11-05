package com.scaffold.lowcode.page.domain;

import jakarta.validation.constraints.Size;

/**
 * 页面发布请求（PR 7）。
 *
 * <p>发布动作本身没有必填参数：要发的是库里那份设计，仓库与分支都跟着项目走。
 * 这里只收一句给人看的发布说明，它进 {@code lc_page_version.change_desc}，
 * 是半年后回滚时唯一还能读懂「那一版为什么要发」的东西。
 */
public class PagePublishRequest {

    /** 发布说明（写进版本行的 change_desc） */
    @Size(max = 512, message = "发布说明不能超过 512 字")
    private String changeDesc;

    public String getChangeDesc() {
        return changeDesc;
    }

    public void setChangeDesc(String changeDesc) {
        this.changeDesc = changeDesc;
    }
}
