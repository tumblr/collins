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
  function setProvisionerValue(id, profile, values, name_id, label) {
    var config_value = profile[name_id];
    if (config_value) {
      $(id).find('div.input').append('<span id="'+name_id+'" class="uneditable-input">' + config_value + "</span>");
    } else {
      var html = '<select name="'+name_id+'" id="'+name_id+'"><option value="" selected="selected"></option>';
      var help = '';
      values.forEach(function(v) {
        html += '<option value="'+v+'">'+v+'</option>'
      });
      if (profile['requires_' + name_id]) {
        help = label + ' is required';
      } else {
        help = label + ' is optional';
      }
      help = '<span class="help-block">' + help + '</span>';
      var input = $(id).find('div.input')
      input.append(html)
      input.append(help);
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
      setProvisionerValue(primary_role, profile, config["primary_roles"], 'primary_role', 'Primary Role');
      setProvisionerValue(pool, profile, config["pools"], 'pool', 'Pool');
      setProvisionerValue(secondary_role, profile, config["secondary_roles"], 'secondary_role', 'Secondary Role');
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
