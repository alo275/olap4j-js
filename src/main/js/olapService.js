/*global define */
define(['jquery'],function($){
  function OlapService(svcUrl) {

    function hydrateAxisData(data) {
      var members = {}, r, c, position, memberDescriptor, memberId;
      for (r = 0; r < data.length; r++) {
        position = data[r];
        for (c = 0; c < position.length; c++) {
          memberDescriptor = position[c];
          if (typeof(memberDescriptor.member) === "number") {
            // Member descriptor contains the member id, replace it with the member itself
            memberId = memberDescriptor.member;
            memberDescriptor.member = members[memberId];
          } else {
            // Member contains the member description, store it in the members map
            members[memberDescriptor.member.id] = memberDescriptor.member;
          }
        }
      }
    }

    function postProcessData(data, options) {
      if ( !data.axes ) {
        return;
      }

      for(var i = 0;i < data.axes.length; i++ ) {
        hydrateAxisData(data.axes[i].positions);
      }
      options.success(data);
    }

    function drillUndrill(op, axisOrdinal,position, options) {
      var posNames = [], i;
      for(i = 0; i < position.length; ++i) {
        posNames[i] = position[i].uniqueName;
      }
      $.ajax({
        type:'post',
        url:svcUrl+'/'+op,
        data : {
          axis:axisOrdinal,
          position:posNames
        },
        success:function(data) {
          postProcessData(data, options);
        }
      });
    }


    this.executeQuery = function(options){
      $.ajax({
        type:'get',
        url:svcUrl,
        success:function(data) {
          postProcessData(data, options);
        },
        error:function(jqXHR, textStatus, error){
          alert(textStatus+'-'+error);
        }
      });
    };

    this.drill = function(axisOrdinal,position, options){
      drillUndrill('drill', axisOrdinal, position, options);
    };

    this.undrill = function(axisOrdinal,position, options){
      drillUndrill('undrill', axisOrdinal, position, options);
    };

    this.move = function(hierarchyName, axisOrdinal, position, options) {
      $.ajax({
        type:'post',
        url:svcUrl+'/hierarchies/move',
        data: {
          hierarchy:hierarchyName,
          axis:axisOrdinal,
          position:position
        },
        success:function(data){
          postProcessData(data,options);
        }
      });
    };

    this.addHierarchy = function(axisOrdinal, hierarchyId, options) {
      $.ajax({
        type:'post',
        url:svcUrl+'/hierarchies/add',
        data : {
          axis:axisOrdinal,
          hierarchy:hierarchyId
        },
        success:function(data) {
          postProcessData(data, options);
        }
      });
    };

    this.removeHierarchy = function(axisOrdinal, hierarchyId, options) {
      $.ajax({
        type:'post',
        url:svcUrl+'/hierarchies/remove',
        data : {
          axis:axisOrdinal,
          hierarchy:hierarchyId
        },
        success:function(data) {
          postProcessData(data, options);
        }
      });
    };
  }
  return OlapService;
});
