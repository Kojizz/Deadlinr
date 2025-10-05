package com.deadlinr.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PageTreeNode(
        Long id,
        String title,
        @Schema(nullable = true) String icon,
        @Schema(nullable = true) Long parentId,
        @Schema(nullable = true) Long courseId,
        boolean favorite,
        double position,
        String kind,
        int childCount,
        boolean hasDueDate,
        List<PageTreeNode> children,
        int openTasks
) {
 public PageTreeNode(Long id,String title,String icon,Long parentId,Long courseId,boolean favorite,double position,List<PageTreeNode> children){
  this(id,title,icon,parentId,courseId,favorite,position,"NOTE",children.size(),false,children,0);
 }
}
