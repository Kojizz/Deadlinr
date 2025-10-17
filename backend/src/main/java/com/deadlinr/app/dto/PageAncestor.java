package com.deadlinr.app.dto;
import io.swagger.v3.oas.annotations.media.Schema;
public record PageAncestor(long id,@Schema(nullable=true) Long parentId,String title,@Schema(nullable=true) String icon){}
