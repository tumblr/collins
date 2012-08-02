require 'spec_helper'

module Collins
  class DecommissionWorkflow < Collins::PersistentState
    manage_state :decommission_process, :initial => :start do |client|

      action :power_off do |asset,s|
        s.logger.info("WTF?")
        client.power! asset, "powerOff"
      end
      action :power_on do |asset|
        client.power! asset, "powerOn"
      end
      action :provision do |asset|
        client.provision asset, "destroy", "blake"
      end

      # After optional expiration, take action and transition. 

      # After 24 hours transition to initial_power_off
      event :start, :desc => 'Starting decommission process', :expires => after(24, :hours), :transition => :initial_power_off
      # Before successfully transitioning, power off, then after another 24 hours transition to power_on_for_cleaning
      event :initial_power_off, :desc => 'Powering off asset for 24 hours', :before_transition => :power_off, :expires => after(24, :hours), :transition => :power_on_for_cleaning
      # Before successfully transitioning, power on, then after 2 hours transition to provision_for_cleaning
      event :power_on_for_cleaning, :desc => 'Power on for cleaning', :before_transition => :power_on, :expires => after(2, :hours), :transition => :provision_for_cleaning
      # Before successfully transitioning, provision the host, then after 72 hours transition to power_off_after_cleaning
      event :provision_for_cleaning, :desc => 'Provision host onto clean image', :before_transition => :provision, :expires => after(72, :hours), :transition => :power_off_after_cleaning
      # Transition successfully (immediately) and power off the host
      event :power_off_after_cleaning, :desc => 'Power off after cleaning and finish', :on_transition => :power_off, :transition => :done
      event :done, :desc => 'All finished', :terminus => true
    end
  end

  class NoTransitionWorkflow < Collins::PersistentState
    manage_state :no_transitions_process, :initial => :start do |client|

      action :reboot_hard do |asset|
        client.power! asset, "rebootHard"
      end

      action :reprovision do |asset|
        detailed = client.get asset
        client.provision asset, detailed.nodeclass, detailed.contact, :suffix => detailed.suffix,
          :primary_role => detailed.primary_role, :secondary_role => detailed.secondary_role,
          :pool => detailed.pool
      end

      action :toggle_status do |asset|
        client.set_status!(asset, 'Provisioning') && client.set_status!(asset, 'Provisioned')
      end

      # No transitions are defined here, since they are managed fully asynchronously by the
      # supervisor process. We only defines actions to take once the timeout has passed. Each
      # part of the process makes an event call, the supervisor process just continuously does a
      # collins.managed_process("ProvisioningWorkflow").transition(asset) which will
      # either execute the on_transition action due to expiration or do nothing.

      # After 30 minutes reprovision if still in the start state - ewr_start_provisioning - collins.managed_process("ProvisioningWorkflow").start(asset)
      event :start, :desc => 'Provisioning Started', :expires => after(30, :minutes), :on_transition => :reprovision
      # After 10 minutes if we haven't seen an ipxe request, reprovision (possible failed move) - vlan changer & provisioning status - collins.mp.vlan_moved_to_provisioning(asset)
      event :vlan_moved_to_provisioning, :desc => 'Moved to provisioning VLAN', :expires => after(10, :minutes), :on_transition => :reprovision
      # After 5 minutes if we haven't yet seen a kickstart request, reboot (possibly stuck at boot) - phil ipxe
      event :ipxe_seen, :desc => 'Asset has made iPXE request to Phil', :expires => after(5, :minutes), :on_transition => :reboot_hard
      # After 5 minutes if the kickstart process hasn't begun, reboot (possibly stuck at boot) - phil kickstart
      event :kickstart_seen, :desc => 'Asset has made kickstart request to Phil', :expires => after(5, :minutes), :on_transition => :reboot_hard
      # After 45 minutes if the kickstart process hasn't completed, reboot to reinstall - phil kickstart pre
      event :kickstart_started, :desc => 'Asset has started kickstart process', :expires => after(45, :minutes), :on_transition => :reboot_hard
      # After 10 minutes if we haven't been moved to the production VLAN, toggle our status to get us moved - phil kickstart post
      event :kickstart_finished, :desc => 'Asset has finished kickstart process', :expires => after(10, :minutes), :on_transition => :toggle_status
      # After another 15 minutes if still not moved toggle our status again - vlan changer when discover=true
      event :vlan_move_to_production, :desc => 'Moved to production VLAN', :expires => after(15, :minutes), :on_transition => :toggle_status
      # After another 10 minutes if we're not reachable by IP, toggle the status - supervisor
      event :reachable_by_ip, :desc => 'Now reachable by IP address', :expires => after(10, :minutes), :on_transition => :toggle_status
      # Place holder for when we finish
      event :done, :desc => 'Finished', :terminus => true
    end
  end
end
