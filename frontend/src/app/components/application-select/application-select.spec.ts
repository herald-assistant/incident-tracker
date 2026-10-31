import { TestBed } from '@angular/core/testing';
import { ApplicationSelectComponent } from './application-select';
describe('ApplicationSelectComponent',()=>{
 it('filters full options and emits the selected system',()=>{
  const f=TestBed.createComponent(ApplicationSelectComponent);f.componentRef.setInput('options',[{systemId:'crm',name:'CRM Customers',summary:'Contact management'}]);
  let id='';f.componentInstance.selected.subscribe(v=>id=v);f.detectChanges();
  f.nativeElement.querySelector('button').click();f.detectChanges();
  f.componentInstance.search.set('contact');f.detectChanges();
  f.nativeElement.querySelector('[role=option]').click();expect(id).toBe('crm');expect(f.componentInstance.open()).toBe(false);
 });
});

