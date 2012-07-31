$(document).ready(function() {

  var primary_role = "#primary-role-container";
  var pool = "#pool-container";
  var secondary_role = "#secondary-role-container";

  function resetContainerInput(id) {
    $(id).find('div.input').each(function() {
      $(this).empty();
    });
  };
  function hideContainer(id) {
    $(id).hide();
  };
  function showContainer(id) {
    $(id).show();
  };
  function setProvisionerValue(id, profile, values, name_id, label, type) {
    var config_value = profile[name_id];
    if (config_value) {
      $(id).find('div.input').append('<span id="'+name_id+'" class="uneditable-input">' + config_value + "</span>");
    } else {
      var input = $(id).find('div.input')
      input.empty();
      if ($(id).hasClass('haveChoice')) {
        var check_name = name_id + '_check';
        var check = '';
        if (type == 'text') {
          check = '<input type="checkbox" checked name="'+check_name+'" value="text"> Custom ' + label;
        } else {
          check = '<input type="checkbox" name="'+check_name+'" value="text"> Custom ' + label;
        }
        check = '<div>' + check + '</div>';
        input.append(check);
        $(id).find('input[type=checkbox][name='+check_name+']').each(function() {
          $(this).change(function() {
            if ($(this).is(':checked')) {
              setProvisionerValue(id, profile, values, name_id, label, 'text');
            } else {
              setProvisionerValue(id, profile, values, name_id, label, 'list');
            }
          });
        });
      }
      var help = '';
      if (profile['requires_' + name_id]) {
        help = label + ' is required';
      } else {
        help = label + ' is optional';
      }
      help = '<span class="help-block">' + help + '</span>';
      if (type == 'text') {
        var html = '<input type="text" name="'+name_id+'" id="'+name_id+'">';
        input.append(html);
        input.append(help);
      } else {
        var html = '<select name="'+name_id+'" id="'+name_id+'"><option value="" selected="selected"></option>';
        values.forEach(function(v) {
          html += '<option value="'+v+'">'+v+'</option>'
        });
        input.append(html)
        input.append(help);
      }
    }
  };
  function isEmpty(v) {
    var result = false;
    if (typeof(v) == 'string') {
      result = (!v || v.replace(/\s/g,"") == "");
    } else {
      result = (typeof(v) === 'undefined');
    }
    return result;
  };

  function updateProvisionerValues(config, value) {
    var profiles = config["profiles"] || {};
    var profile = profiles[value];
    var containers = [primary_role, pool, secondary_role];
    containers.forEach(function(v) {
      resetContainerInput(v);
    });
    if (isEmpty(profile) || isEmpty(value)) {
      containers.forEach(function(v) {
        hideContainer(v);
      });
    } else {
      setProvisionerValue(primary_role, profile, config["primary_roles"], 'primary_role', 'Primary Role', 'List');
      setProvisionerValue(pool, profile, config["pools"], 'pool', 'Pool', 'List');
      setProvisionerValue(secondary_role, profile, config["secondary_roles"], 'secondary_role', 'Secondary Role', 'List');
      containers.forEach(function(v) {
        showContainer(v);
      });
    }
  };

  $("[data-triggers]").each(function() {
    var el = $(this);
    var config_var = el.attr('data-triggers');
    var config = window[config_var];
    el.change(function() {
      var option = $(this);
      var value = option.val();
      updateProvisionerValues(config, value);
    });
  });
});
