require 'collins_state'

module Collins
  class ProvisioningWorkflow < ::Collins::PersistentState

    manage_state :provisioning_process, :initial => :start do |client|

      action :reboot_hard do |asset, p|
        p.logger.warn "Calling rebootHard on asset #{Collins::Util.get_asset_or_tag(asset).tag}"
        client.power! asset, "rebootHard"
      end

      action :reprovision do |asset, p|
        detailed = client.get asset
        p.logger.warn "Reprovisioning #{detailed.tag}"
        client.set_status! asset, "Maintenance", "Automated reprovisioning"
        client.provision asset, detailed.nodeclass, (detailed.build_contact || "nobody"), :suffix => detailed.suffix,
          :primary_role => detailed.primary_role, :secondary_role => detailed.secondary_role,
          :pool => detailed.pool
      end

      action :toggle_status do |asset, p|
        tag = Collins::Util.get_asset_or_tag(asset).tag
        p.logger.warn "Toggling status (Provisioning then Provisioned) for #{tag}"
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
      event :vlan_moved_to_provisioning, :desc => 'Moved to provisioning VLAN', :expires => after(15, :minutes), :on_transition => :reprovision
      # After 5 minutes if we haven't yet seen a kickstart request, reboot (possibly stuck at boot) - phil ipxe
      event :ipxe_seen, :desc => 'Asset has made iPXE request to Phil', :expires => after(5, :minutes), :on_transition => :reboot_hard
      # After 5 minutes if the kickstart process hasn't begun, reboot (possibly stuck at boot) - phil kickstart
      event :kickstart_seen, :desc => 'Asset has made kickstart request to Phil', :expires => after(15, :minutes), :on_transition => :reboot_hard
      # After 45 minutes if we haven't gotten to post, reboot to reinstall - phil kickstart pre
      event :kickstart_started, :desc => 'Asset has started kickstart process', :expires => after(45, :minutes), :on_transition => :reboot_hard
      # After 45 minutes if the kickstart process hasn't completed, reboot to reinstall - phil kickstart post
      event :kickstart_post_started, :desc => 'Asset has started kickstart post section', :expires => after(45, :minutes), :on_transition => :reboot_hard
      # After 10 minutes if we haven't been moved to the production VLAN, toggle our status to get us moved - phil kickstart post
      event :kickstart_finished, :desc => 'Asset has finished kickstart process', :expires => after(15, :minutes), :on_transition => :toggle_status
      # After another 15 minutes if still not moved toggle our status again - vlan changer when discover=true
      event :vlan_move_to_production, :desc => 'Moved to production VLAN', :expires => after(15, :minutes), :on_transition => :toggle_status
      # After another 10 minutes if we're not reachable by IP, toggle the status - supervisor
      event :reachable_by_ip, :desc => 'Now reachable by IP address', :expires => after(10, :minutes), :on_transition => :toggle_status
      # Place holder for when we finish
      event :done, :desc => 'Finished', :terminus => true
    end
  end

end
